#!/usr/bin/env python3
"""Apply/check the same formatters locally, from Codex hooks, and in CI.

Examples (file arguments are repository-relative):
  python3 scripts/format.py apply backend/src/main/java/example/Example.java
  python3 scripts/format.py check --base origin/dev --stack backend
  python3 scripts/format.py apply --all --stack frontend

Swift formatting uses the selected Xcode (16+) on macOS and the installed
Swift toolchain (6+) on Linux. No standalone formatter is installed; its
version follows the active toolchain.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
EXCLUDED = {
    "build", "generated", "DerivedData", ".build", ".swiftpm",
    "Pods", "Carthage", ".cache", ".git",
}


def source_path(value, root=ROOT, cwd=None):
    """Return a safe, existing source path; never follow a symlink outside scope."""
    original = Path(value)
    original = original if original.is_absolute() else (cwd or root) / original
    path = original.resolve()
    try:
        relative = path.relative_to(root.resolve())
    except ValueError:
        return None
    if not path.is_file() or any(part in EXCLUDED for part in relative.parts):
        return None
    if path.suffix == ".java":
        if relative.parts[:4] not in (
            ("backend", "src", "main", "java"),
            ("backend", "src", "test", "java"),
        ):
            return None
    elif path.suffix == ".swift":
        if relative.parts[0] != "frontend":
            return None
    else:
        return None
    return path


def content_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def collect_all(root=ROOT):
    paths = []
    for folder in (root / "backend/src", root / "frontend"):
        if not folder.is_dir():
            continue
        for directory, folders, files in os.walk(folder, followlinks=False):
            folders[:] = [name for name in folders if name not in EXCLUDED]
            for name in files:
                path = source_path(Path(directory) / name, root)
                if path:
                    paths.append(path)
    return sorted(set(paths))


def changed_files(base, root=ROOT):
    # Resolve first so caller-supplied refs cannot be interpreted as options.
    resolved = subprocess.run(
        ["git", "rev-parse", "--verify", "--end-of-options", base + "^{commit}"],
        cwd=root, check=True, capture_output=True, text=True,
    ).stdout.strip()
    output = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMR", "-z", resolved, "HEAD", "--"],
        cwd=root, check=True, capture_output=True,
    ).stdout
    return [
        path for name in output.split(b"\0") if name
        if (path := source_path(os.fsdecode(name), root))
    ]


def swift_command(root=ROOT):
    # Use a toolchain entry point, not Homebrew/PATH swift-format binaries or
    # the old standalone cache. xcrun respects Xcode and DEVELOPER_DIR.
    if sys.platform == "darwin":
        command = ["xcrun", "swift-format"]
        message = (
            "Xcode's bundled swift-format is unavailable. Install/select Xcode 16+ "
            "and check xcode-select -p or DEVELOPER_DIR."
        )
    elif sys.platform.startswith("linux"):
        command = ["swift", "format"]
        message = (
            "The Swift toolchain's bundled formatter is unavailable. "
            "Install/select a Swift 6+ toolchain and ensure swift is on PATH."
        )
    else:
        raise RuntimeError("Swift formatting is supported on macOS and Linux")
    if not shutil.which(command[0]):
        raise RuntimeError(message)
    try:
        subprocess.run(
            [*command, "--version"], capture_output=True, text=True,
            check=True, timeout=10,
        )
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        raise RuntimeError(message) from error
    return command


def formatter_commands(mode, paths, root=ROOT):
    commands = []
    java = [path for path in paths if path.suffix == ".java"]
    swift = [path for path in paths if path.suffix == ".swift"]
    if java:
        task = "spotlessJavaApply" if mode == "apply" else "spotlessJavaCheck"
        # Explicit target lists avoid plugin-version-specific IDE filtering and globs.
        for offset in range(0, len(java), 50):
            selected = json.dumps(list(map(str, java[offset:offset + 50])))
            commands.append((
                ["bash", str(root / "backend/gradlew"), task, "--console=plain", "-q",
                 "-PcalioFormatFiles=" + selected], root / "backend",
            ))
    if swift:
        command = swift_command(root)
        flags = ["format", "--in-place"] if mode == "apply" else ["lint", "--strict"]
        # Batch without shell expansion, preserving spaces and bounding argv size.
        for offset in range(0, len(swift), 50):
            commands.append((
                [*command, *flags, "--configuration", str(root / "frontend/.swift-format"),
                 *map(str, swift[offset:offset + 50])], root,
            ))
    return commands


def run_formatters(mode, paths, root=ROOT):
    for command, cwd in formatter_commands(mode, paths, root):
        try:
            result = subprocess.run(command, cwd=cwd, capture_output=True, text=True, timeout=240)
        except subprocess.TimeoutExpired as error:
            raise RuntimeError("Formatter timed out after 240 seconds") from error
        if result.returncode:
            # Keep hook feedback bounded; don't flood the model with build logs.
            details = (result.stderr or result.stdout).strip()[-3000:]
            raise RuntimeError(f"Formatter failed ({result.returncode}):\n{details}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("apply", "check"))
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--all", action="store_true", help="Explicitly select all source files")
    selection.add_argument("--base", help="Check committed changes between this Git ref and HEAD")
    parser.add_argument("--stack", choices=("backend", "frontend"))
    parser.add_argument("files", nargs="*")
    args = parser.parse_args(argv)
    if args.files and (args.all or args.base):
        parser.error("Use file arguments, --all, or --base, not a combination")
    if not args.files and not args.all and not args.base:
        parser.error("Select files, --all, or --base; no implicit whole-repository formatting")
    try:
        if args.all:
            paths = collect_all()
        elif args.base:
            paths = changed_files(args.base)
        else:
            paths = []
            for name in args.files:
                path = source_path(name)
                if not path:
                    raise RuntimeError(f"Not an eligible source file: {name}")
                paths.append(path)
        if args.stack:
            suffix = ".java" if args.stack == "backend" else ".swift"
            paths = [path for path in paths if path.suffix == suffix]
        paths = sorted(set(paths))
        run_formatters(args.mode, paths)
        print(f"Format {args.mode}: {len(paths)} file(s)")
        return 0
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
