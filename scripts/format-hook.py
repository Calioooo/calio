#!/usr/bin/env python3
"""Track apply_patch content changes; format only pending sources at Stop.

State stays in the ignored .cache/formatters/hooks directory, never in prompts.
Shell/MCP writes are not attributed by this hook; CI remains the final check.
"""

from contextlib import contextmanager
import fcntl
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import subprocess
import sys


spec = spec_from_file_location("calio_format", Path(__file__).with_name("format.py"))
format_code = module_from_spec(spec)
spec.loader.exec_module(format_code)


def patch_paths(command):
    paths = []
    current = None
    for line in command.splitlines():
        if line.startswith(("*** Add File: ", "*** Update File: ")):
            current = line.split(": ", 1)[1]
            paths.append(current)
        elif line.startswith("*** Delete File: "):
            current = None
        elif line.startswith("*** Move to: ") and current:
            paths.append(line.split(": ", 1)[1])
    return list(dict.fromkeys(paths))


def candidate_path(name, root, cwd):
    # Include nonexistent Add/Move targets in the pre-edit snapshot.
    path = Path(name)
    path = (path if path.is_absolute() else cwd / path).resolve()
    try:
        relative = path.relative_to(root)
    except ValueError:
        return None
    if any(part in format_code.EXCLUDED for part in relative.parts):
        return None
    if path.suffix == ".java" and relative.parts[:4] in (
        ("backend", "src", "main", "java"), ("backend", "src", "test", "java"),
    ):
        return path
    if path.suffix == ".swift" and relative.parts[0] == "frontend":
        return path
    return None


@contextmanager
def locked_state(root, session):
    key = hashlib.sha256(session.encode()).hexdigest()
    folder = root / ".cache/formatters/hooks"
    folder.mkdir(parents=True, exist_ok=True)
    with (folder / (key + ".lock")).open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        file = folder / (key + ".json")
        state = json.loads(file.read_text()) if file.exists() else {}
        try:
            yield state
        finally:
            # All readers/writers hold the same stable lock file.
            temporary = file.with_suffix(".tmp")
            temporary.write_text(json.dumps(state))
            temporary.replace(file)


def process(event, root, runner=None):
    runner = runner or format_code.run_formatters
    root = root.resolve()
    cwd = Path(event["cwd"]).resolve()
    session = event.get("session_id")
    kind = event.get("hook_event_name")
    if not session or kind not in ("PreToolUse", "PostToolUse", "Stop"):
        return {}
    if kind != "Stop" and event.get("tool_name") != "apply_patch":
        return {}
    if kind == "Stop":
        key = hashlib.sha256(session.encode()).hexdigest()
        if not (root / ".cache/formatters/hooks" / (key + ".json")).exists():
            return {}
    with locked_state(root, session) as state:
        pending = state.setdefault("pending", {})
        if kind in ("PreToolUse", "PostToolUse"):
            command = event.get("tool_input", {}).get("command", "")
            paths = [
                path for name in patch_paths(command)
                if (path := candidate_path(name, root, cwd))
            ]
            tool = event.get("tool_use_id")
            if not tool:
                raise RuntimeError("apply_patch hook input is missing tool_use_id")
            snapshots = state.setdefault("snapshots", {})
            if kind == "PreToolUse":
                snapshots[tool] = {
                    str(path.relative_to(root)): format_code.content_hash(path) for path in paths
                }
            else:
                before = snapshots.pop(tool, None)
                if before is None:
                    # Don't infer ownership from an existing dirty worktree.
                    raise RuntimeError("Missing pre-edit snapshot; cannot safely attribute files")
                for path in paths:
                    name = str(path.relative_to(root))
                    eligible = format_code.source_path(path, root)
                    if eligible:
                        after = format_code.content_hash(eligible)
                        if after != before.get(name):
                            pending[name] = after
            return {}
        paths = [
            path for name in pending if (path := format_code.source_path(name, root))
        ]
        if not paths:
            pending.clear()
            state.pop("last_failure", None)
            return {}
        before = {str(path.relative_to(root)): format_code.content_hash(path) for path in paths}
        fingerprint = hashlib.sha256(json.dumps(before, sort_keys=True).encode()).hexdigest()
        if state.get("last_failure") == fingerprint:
            # A Q&A or unchanged continuation must not repeatedly invoke a failing formatter.
            return {}
        try:
            runner("apply", paths, root)
        except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
            state["last_failure"] = hashlib.sha256(json.dumps(
                {str(path.relative_to(root)): format_code.content_hash(path) for path in paths},
                sort_keys=True,
            ).encode()).hexdigest()
            message = f"자동 포맷 실패: {error}"[:3500]
            if event.get("stop_hook_active"):
                return {"systemMessage": message}
            return {"decision": "block", "reason": message + "\n원인을 해결하고 포맷·검증 결과를 확인하세요."}
        pending.clear()
        state.pop("last_failure", None)
        changed = [name for name, value in before.items() if format_code.content_hash(root / name) != value]
        if changed:
            names = ", ".join(changed[:5])[:500]
            message = f"자동 포맷으로 {len(changed)}개 파일이 변경되었습니다: {names}. 최종 diff와 관련 검증을 확인하세요."
            if event.get("stop_hook_active"):
                return {"systemMessage": message}
            return {"decision": "block", "reason": message}
        return {}


def main():
    try:
        event = json.load(sys.stdin)
        root = Path(subprocess.run(
            ["git", "rev-parse", "--show-toplevel"], cwd=event["cwd"],
            check=True, capture_output=True, text=True,
        ).stdout.strip())
        print(json.dumps(process(event, root), ensure_ascii=False))
        return 0
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.CalledProcessError) as error:
        # Never silently report success on malformed input or corrupted state.
        print(f"Formatter hook error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
