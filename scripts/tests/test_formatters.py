import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parent.parent


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


formatter = load("formatter", "format.py")
hook = load("hook", "format-hook.py")
ci = load("ci", "format-ci.py")


class WorkspaceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="calio-format-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        self.java_name = "backend/src/main/java/example/Example.java"
        self.swift_name = "frontend/Calio/Example.swift"

    def write(self, name, text="before"):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def event(self, kind, command="", session="session-1", tool="tool-1", cwd=None):
        return {
            "session_id": session, "cwd": str(cwd or self.root),
            "hook_event_name": kind, "tool_name": "apply_patch",
            "tool_use_id": tool, "tool_input": {"command": command},
            "stop_hook_active": False,
        }

    def track(self, name=None, text="after", session="session-1", tool="tool-1"):
        name = name or self.java_name
        command = "*** Begin Patch\n*** Update File: " + name + "\n*** End Patch"
        hook.process(self.event("PreToolUse", command, session, tool), self.root)
        path = self.write(name, text)
        hook.process(self.event("PostToolUse", command, session, tool), self.root)
        return path


class SourceSelectionTests(WorkspaceTest):
    def test_java_sources_and_tests_are_eligible(self):
        for name in (self.java_name, "backend/src/test/java/example/ExampleTest.java"):
            path = self.write(name)
            self.assertEqual(formatter.source_path(name, self.root), path)

    def test_swift_sources_are_eligible(self):
        path = self.write(self.swift_name)
        self.assertEqual(formatter.source_path(self.swift_name, self.root), path)

    def test_generated_and_dependency_sources_are_excluded(self):
        for name in (
            "backend/src/main/java/generated/Example.java",
            "frontend/Calio/build/Example.swift", "frontend/Pods/Example.swift",
            "frontend/DerivedData/Example.swift", "frontend/.build/Example.swift",
        ):
            self.write(name)
            self.assertIsNone(formatter.source_path(name, self.root))

    def test_non_sources_and_wrong_stack_are_excluded(self):
        for name in ("backend/Example.java", "frontend/Example.java", "backend/Example.swift", "docs/Example.md"):
            self.write(name)
            self.assertIsNone(formatter.source_path(name, self.root))

    def test_deleted_file_is_excluded(self):
        self.assertIsNone(formatter.source_path(self.java_name, self.root))

    def test_symlink_outside_repository_is_excluded(self):
        with tempfile.TemporaryDirectory(prefix="calio-format-outside-") as folder:
            outside = Path(folder) / "Outside.java"
            outside.write_text("outside")
            link = self.root / self.java_name
            link.parent.mkdir(parents=True)
            link.symlink_to(outside)
            self.assertIsNone(formatter.source_path(link, self.root))

    def test_collect_all_skips_generated_files(self):
        java = self.write(self.java_name)
        swift = self.write(self.swift_name)
        self.write("frontend/Pods/Generated.swift")
        self.assertEqual(set(formatter.collect_all(self.root)), {java, swift})

    def test_java_target_list_preserves_spaces_and_commas(self):
        path = self.write("backend/src/main/java/example/Space , Name.java")
        commands = formatter.formatter_commands("check", [path], self.root)
        arguments, cwd = commands[0]
        self.assertEqual(cwd, self.root / "backend")
        self.assertIn("spotlessJavaCheck", arguments)
        self.assertEqual(json.loads(arguments[-1].split("=", 1)[1]), [str(path)])

    def test_java_targets_are_batched(self):
        paths = [self.write(f"backend/src/main/java/example/Example{i}.java") for i in range(51)]
        commands = formatter.formatter_commands("apply", paths, self.root)
        self.assertEqual(len(commands), 2)
        self.assertEqual(len(json.loads(commands[0][0][-1].split("=", 1)[1])), 50)

    def test_swift_commands_use_same_config_for_apply_and_check(self):
        path = self.write(self.swift_name)
        for command in (["xcrun", "swift-format"], ["swift", "format"]):
            with self.subTest(command=command), \
                    patch.object(formatter, "swift_command", return_value=command):
                apply = formatter.formatter_commands("apply", [path], self.root)[0][0]
                check = formatter.formatter_commands("check", [path], self.root)[0][0]
                self.assertIn("--in-place", apply)
                self.assertIn("--strict", check)
                self.assertEqual(apply[:3], [*command, "format"])
                self.assertEqual(check[:3], [*command, "lint"])
                self.assertIn(str(self.root / "frontend/.swift-format"), apply)
                self.assertIn(str(self.root / "frontend/.swift-format"), check)

    def test_no_files_do_not_resolve_or_run_tools(self):
        with patch.object(formatter, "swift_command") as swift, patch.object(formatter.subprocess, "run") as run:
            formatter.run_formatters("apply", [], self.root)
            swift.assert_not_called()
            run.assert_not_called()

    def test_failed_formatter_propagates_failure(self):
        path = self.write(self.java_name)
        result = subprocess.CompletedProcess([], 1, "", "format failed")
        with patch.object(formatter.subprocess, "run", return_value=result):
            with self.assertRaisesRegex(RuntimeError, "format failed"):
                formatter.run_formatters("apply", [path], self.root)

    def test_formatter_timeout_is_reported(self):
        path = self.write(self.java_name)
        with patch.object(formatter.subprocess, "run", side_effect=subprocess.TimeoutExpired([], 240)):
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                formatter.run_formatters("apply", [path], self.root)

    def test_swift_uses_selected_xcode_without_version_pin(self):
        for version in ("602.0.0", "603.0.0"):
            with self.subTest(version=version), \
                    patch.object(formatter.sys, "platform", "darwin"), \
                    patch.object(formatter.shutil, "which", return_value="/usr/bin/xcrun") as which, \
                    patch.object(formatter.subprocess, "run", return_value=
                                 subprocess.CompletedProcess([], 0, version, "")) as run:
                self.assertEqual(formatter.swift_command(self.root), ["xcrun", "swift-format"])
                which.assert_called_once_with("xcrun")
                run.assert_called_once_with(
                    ["xcrun", "swift-format", "--version"], capture_output=True,
                    text=True, check=True, timeout=10,
                )

    def test_swift_ignores_old_standalone_cache(self):
        self.write(".cache/formatters/swift-format-603.0.0/.build/release/swift-format")
        with patch.object(formatter.sys, "platform", "darwin"), \
                patch.object(formatter.shutil, "which", return_value="/usr/bin/xcrun"), \
                patch.object(formatter.subprocess, "run", return_value=
                             subprocess.CompletedProcess([], 0, "602.0.0", "")):
            self.assertEqual(formatter.swift_command(self.root), ["xcrun", "swift-format"])

    def test_missing_xcrun_is_reported_without_installing_anything(self):
        with patch.object(formatter.sys, "platform", "darwin"), \
                patch.object(formatter.shutil, "which", return_value=None) as which, \
                patch.object(formatter.subprocess, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "Install/select Xcode 16"):
                formatter.swift_command(self.root)
            which.assert_called_once_with("xcrun")
            run.assert_not_called()

    def test_missing_bundled_formatter_is_reported_without_fallback(self):
        with patch.object(formatter.sys, "platform", "darwin"), \
                patch.object(formatter.shutil, "which", return_value="/usr/bin/xcrun"), \
                patch.object(formatter.subprocess, "run", side_effect=
                             subprocess.CalledProcessError(1, ["xcrun", "swift-format"])) as run:
            with self.assertRaisesRegex(RuntimeError, "xcode-select"):
                formatter.swift_command(self.root)
            self.assertEqual(run.call_count, 1)

    def test_xcrun_timeout_is_reported(self):
        with patch.object(formatter.sys, "platform", "darwin"), \
                patch.object(formatter.shutil, "which", return_value="/usr/bin/xcrun"), \
                patch.object(formatter.subprocess, "run", side_effect=
                             subprocess.TimeoutExpired([], 10)):
            with self.assertRaisesRegex(RuntimeError, "bundled swift-format is unavailable"):
                formatter.swift_command(self.root)

    def test_xcrun_os_error_is_reported(self):
        with patch.object(formatter.sys, "platform", "darwin"), \
                patch.object(formatter.shutil, "which", return_value="/usr/bin/xcrun"), \
                patch.object(formatter.subprocess, "run", side_effect=OSError("unavailable")):
            with self.assertRaisesRegex(RuntimeError, "bundled swift-format is unavailable"):
                formatter.swift_command(self.root)

    def test_linux_uses_swift_toolchain_formatter_without_version_pin(self):
        with patch.object(formatter.sys, "platform", "linux"), \
                patch.object(formatter.shutil, "which", return_value="/opt/swift/bin/swift") as which, \
                patch.object(formatter.subprocess, "run", return_value=
                             subprocess.CompletedProcess([], 0, "602.0.0", "")) as run:
            self.assertEqual(formatter.swift_command(self.root), ["swift", "format"])
            which.assert_called_once_with("swift")
            run.assert_called_once_with(
                ["swift", "format", "--version"], capture_output=True,
                text=True, check=True, timeout=10,
            )

    def test_missing_linux_swift_is_reported_without_installing(self):
        with patch.object(formatter.sys, "platform", "linux"), \
                patch.object(formatter.shutil, "which", return_value=None), \
                patch.object(formatter.subprocess, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "Swift 6"):
                formatter.swift_command(self.root)
            run.assert_not_called()

    def test_linux_missing_format_subcommand_is_reported(self):
        with patch.object(formatter.sys, "platform", "linux"), \
                patch.object(formatter.shutil, "which", return_value="/opt/swift/bin/swift"), \
                patch.object(formatter.subprocess, "run", side_effect=
                             subprocess.CalledProcessError(1, ["swift", "format"])) as run:
            with self.assertRaisesRegex(RuntimeError, "Swift 6"):
                formatter.swift_command(self.root)
            self.assertEqual(run.call_count, 1)

    def test_linux_formatter_probe_timeout_is_reported(self):
        with patch.object(formatter.sys, "platform", "linux"), \
                patch.object(formatter.shutil, "which", return_value="/opt/swift/bin/swift"), \
                patch.object(formatter.subprocess, "run", side_effect=subprocess.TimeoutExpired([], 10)):
            with self.assertRaisesRegex(RuntimeError, "toolchain's bundled formatter is unavailable"):
                formatter.swift_command(self.root)


class HookTests(WorkspaceTest):
    def test_qa_exits_without_formatter_or_state(self):
        def fail(*args):
            self.fail("Q&A must not run formatter")
        self.assertEqual(hook.process(self.event("Stop"), self.root, fail), {})
        self.assertFalse((self.root / ".cache").exists())

    def test_parser_handles_add_update_move_and_ignores_delete(self):
        command = "\n".join([
            "*** Add File: New.swift", "*** Update File: Old.java",
            "*** Move to: New.java", "*** Delete File: Deleted.java",
        ])
        self.assertEqual(hook.patch_paths(command), ["New.swift", "Old.java", "New.java"])

    def test_changed_file_is_formatted_and_success_is_cleared(self):
        self.write(self.java_name)
        path = self.track()
        calls = []
        def run(mode, paths, root):
            calls.append(paths)
        self.assertEqual(hook.process(self.event("Stop"), self.root, run), {})
        self.assertEqual(calls, [[path]])
        hook.process(self.event("Stop"), self.root, run)
        self.assertEqual(len(calls), 1)

    def test_new_file_is_formatted(self):
        command = "*** Add File: " + self.swift_name
        hook.process(self.event("PreToolUse", command), self.root)
        path = self.write(self.swift_name)
        hook.process(self.event("PostToolUse", command), self.root)
        calls = []
        hook.process(self.event("Stop"), self.root, lambda mode, paths, root: calls.extend(paths))
        self.assertEqual(calls, [path])

    def test_unchanged_or_failed_patch_does_not_queue_file(self):
        self.write(self.java_name)
        command = "*** Update File: " + self.java_name
        hook.process(self.event("PreToolUse", command), self.root)
        hook.process(self.event("PostToolUse", command), self.root)
        calls = []
        hook.process(self.event("Stop"), self.root, lambda *args: calls.append(args))
        self.assertEqual(calls, [])

    def test_preexisting_dirty_file_is_not_selected(self):
        untouched = self.write("backend/src/main/java/example/Other.java", "user edits")
        self.write(self.java_name)
        target = self.track()
        calls = []
        hook.process(self.event("Stop"), self.root, lambda mode, paths, root: calls.extend(paths))
        self.assertEqual(calls, [target])
        self.assertEqual(untouched.read_text(), "user edits")

    def test_pending_deleted_file_is_dropped(self):
        self.write(self.java_name)
        self.track().unlink()
        calls = []
        hook.process(self.event("Stop"), self.root, lambda *args: calls.append(args))
        self.assertEqual(calls, [])

    def test_moved_file_queues_destination_only(self):
        old = self.write(self.java_name)
        new_name = "backend/src/main/java/example/Moved.java"
        command = "*** Update File: " + self.java_name + "\n*** Move to: " + new_name
        hook.process(self.event("PreToolUse", command), self.root)
        new = old.rename(self.root / new_name)
        hook.process(self.event("PostToolUse", command), self.root)
        calls = []
        hook.process(self.event("Stop"), self.root, lambda mode, paths, root: calls.extend(paths))
        self.assertEqual(calls, [new])

    def test_subdirectory_cwd_resolves_patch_paths(self):
        self.write(self.java_name)
        command = "*** Update File: src/main/java/example/Example.java"
        cwd = self.root / "backend"
        hook.process(self.event("PreToolUse", command, cwd=cwd), self.root)
        path = self.write(self.java_name, "after")
        hook.process(self.event("PostToolUse", command, cwd=cwd), self.root)
        calls = []
        hook.process(self.event("Stop", cwd=cwd), self.root, lambda mode, paths, root: calls.extend(paths))
        self.assertEqual(calls, [path])

    def test_sessions_are_isolated(self):
        self.write(self.java_name)
        self.track(session="first")
        calls = []
        hook.process(self.event("Stop", session="second"), self.root, lambda *args: calls.append(args))
        self.assertEqual(calls, [])

    def test_edit_after_success_is_queued_again(self):
        self.write(self.java_name)
        self.track()
        calls = []
        runner = lambda *args: calls.append(args)
        hook.process(self.event("Stop"), self.root, runner)
        self.track(text="new edit", tool="tool-2")
        hook.process(self.event("Stop"), self.root, runner)
        self.assertEqual(len(calls), 2)

    def test_formatter_changes_request_final_diff_review_once(self):
        self.write(self.java_name)
        path = self.track()
        def run(*args):
            path.write_text("formatted")
        result = hook.process(self.event("Stop"), self.root, run)
        self.assertEqual(result["decision"], "block")
        self.assertIn("최종 diff", result["reason"])
        self.assertEqual(hook.process(self.event("Stop"), self.root, run), {})

    def test_stop_continuation_does_not_loop(self):
        self.write(self.java_name)
        path = self.track()
        event = self.event("Stop")
        event["stop_hook_active"] = True
        result = hook.process(event, self.root, lambda *args: path.write_text("formatted"))
        self.assertIn("systemMessage", result)
        self.assertNotIn("decision", result)

    def test_failure_is_not_retried_until_file_changes(self):
        self.write(self.java_name)
        self.track()
        calls = []
        def fail(*args):
            calls.append(args)
            raise RuntimeError("formatter missing")
        self.assertEqual(hook.process(self.event("Stop"), self.root, fail)["decision"], "block")
        self.assertEqual(hook.process(self.event("Stop"), self.root, fail), {})
        self.assertEqual(len(calls), 1)
        self.track(text="fixed", tool="tool-2")
        hook.process(self.event("Stop"), self.root, fail)
        self.assertEqual(len(calls), 2)

    def test_corrupted_state_is_reported(self):
        self.write(self.java_name)
        self.track()
        state_file = next((self.root / ".cache/formatters/hooks").glob("*.json"))
        state_file.write_text("invalid JSON")
        with self.assertRaises(ValueError):
            hook.process(self.event("Stop"), self.root)

    def test_two_tool_snapshots_do_not_overwrite_each_other(self):
        first = self.write(self.java_name)
        name = "backend/src/main/java/example/Other.java"
        second = self.write(name)
        command1 = "*** Update File: " + self.java_name
        command2 = "*** Update File: " + name
        hook.process(self.event("PreToolUse", command1, tool="first"), self.root)
        hook.process(self.event("PreToolUse", command2, tool="second"), self.root)
        first.write_text("first change")
        second.write_text("second change")
        hook.process(self.event("PostToolUse", command2, tool="second"), self.root)
        hook.process(self.event("PostToolUse", command1, tool="first"), self.root)
        calls = []
        hook.process(self.event("Stop"), self.root, lambda mode, paths, root: calls.extend(paths))
        self.assertEqual(set(calls), {first, second})

    def test_repeated_edits_to_same_file_are_deduplicated(self):
        self.write(self.java_name)
        path = self.track()
        self.track(text="another edit", tool="tool-2")
        calls = []
        hook.process(self.event("Stop"), self.root, lambda mode, paths, root: calls.extend(paths))
        self.assertEqual(calls, [path])

    def test_post_without_pre_snapshot_is_reported(self):
        self.write(self.java_name)
        event = self.event("PostToolUse", "*** Update File: " + self.java_name)
        with self.assertRaisesRegex(RuntimeError, "snapshot"):
            hook.process(event, self.root)

    def test_outside_paths_are_not_candidates(self):
        self.assertIsNone(hook.candidate_path("/tmp/Outside.java", self.root, self.root))

    def test_bash_and_other_events_are_ignored(self):
        event = self.event("PostToolUse")
        event["tool_name"] = "Bash"
        self.assertEqual(hook.process(event, self.root), {})
        self.assertFalse((self.root / ".cache").exists())


class GitScopeTests(WorkspaceTest):
    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.root, check=True, capture_output=True, text=True).stdout.strip()

    def setUp(self):
        super().setUp()
        self.git("init", "-q")
        self.git("config", "user.email", "format-test@example.invalid")
        self.git("config", "user.name", "Formatter Test")
        self.git("config", "core.autocrlf", "false")
        self.write(self.java_name)
        self.write(self.swift_name)
        self.git("add", ".")
        self.git("commit", "-qm", "initial")
        self.base = self.git("rev-parse", "HEAD")

    def test_diff_includes_added_modified_renamed_and_excludes_deleted(self):
        self.write(self.java_name, "changed")
        new = self.write("frontend/Calio/New.swift")
        renamed = self.root / "frontend/Calio/Moved.swift"
        (self.root / self.swift_name).rename(renamed)
        self.git("add", "-A")
        self.git("commit", "-qm", "changes")
        self.assertEqual(set(formatter.changed_files(self.base, self.root)), {
            self.root / self.java_name, new, renamed,
        })

    def test_uncommitted_changes_are_not_part_of_ci_scope(self):
        self.write(self.java_name, "uncommitted")
        self.assertEqual(formatter.changed_files(self.base, self.root), [])

    def test_invalid_base_fails_instead_of_silently_skipping(self):
        with self.assertRaises(subprocess.CalledProcessError):
            formatter.changed_files("missing-ref", self.root)

    def test_pull_request_scope_uses_merge_base(self):
        event = {"pull_request": {"base": {"sha": self.base}}}
        self.assertEqual(ci.base_commit("pull_request", event, self.root), self.base)

    def test_push_scope_and_manual_fallback(self):
        self.assertEqual(ci.base_commit("push", {"before": self.base}, self.root), self.base)
        self.assertIsNone(ci.base_commit("push", {"before": "0" * 40}, self.root))
        self.assertIsNone(ci.base_commit("workflow_dispatch", {}, self.root))


class CIScriptTests(WorkspaceTest):
    def test_count_does_not_resolve_or_run_formatters(self):
        path = self.write(self.swift_name)
        with patch("sys.argv", ["format-ci.py", "frontend", "--count"]), \
                patch.dict(ci.os.environ, {}, clear=True), \
                patch.object(ci.format_code, "collect_all", return_value=[path]), \
                patch.object(ci.format_code, "run_formatters") as run, \
                patch("sys.stdout", new=io.StringIO()) as output:
            self.assertEqual(ci.main(), 0)
            self.assertEqual(output.getvalue().strip(), "1")
            run.assert_not_called()

    def test_empty_scope_does_not_select_other_stack_sources(self):
        java = self.write(self.java_name)
        with patch("sys.argv", ["format-ci.py", "frontend"]), \
                patch.dict(ci.os.environ, {}, clear=True), \
                patch.object(ci.format_code, "collect_all", return_value=[java]), \
                patch.object(ci.format_code, "run_formatters") as run, \
                patch("sys.stdout", new=io.StringIO()):
            self.assertEqual(ci.main(), 0)
            run.assert_called_once_with("check", [])


if __name__ == "__main__":
    unittest.main()
