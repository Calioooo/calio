#!/usr/bin/env python3
"""Check changed committed sources in PR/push CI; manual runs check all sources."""

import argparse
from importlib.util import module_from_spec, spec_from_file_location
import json
import os
from pathlib import Path
import subprocess
import sys


spec = spec_from_file_location("calio_format", Path(__file__).with_name("format.py"))
format_code = module_from_spec(spec)
spec.loader.exec_module(format_code)


def base_commit(event_name, event, root):
    if event_name == "pull_request":
        # Check the complete PR, not only the most recent commit.
        base = event["pull_request"]["base"]["sha"]
        return subprocess.run(
            ["git", "merge-base", base, "HEAD"], cwd=root,
            check=True, capture_output=True, text=True,
        ).stdout.strip()
    if event_name == "push":
        base = event.get("before")
        if base and set(base) != {"0"}:
            return base
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stack", choices=("backend", "frontend"))
    parser.add_argument("--count", action="store_true", help="Print source count without resolving formatters")
    args = parser.parse_args()
    try:
        event_file = os.environ.get("GITHUB_EVENT_PATH")
        event = json.loads(Path(event_file).read_text()) if event_file else {}
        base = base_commit(os.environ.get("GITHUB_EVENT_NAME"), event, format_code.ROOT)
        paths = format_code.changed_files(base) if base else format_code.collect_all()
        suffix = ".java" if args.stack == "backend" else ".swift"
        paths = sorted({path for path in paths if path.suffix == suffix})
        if args.count:
            print(len(paths))
        else:
            format_code.run_formatters("check", paths)
            print(f"Format check: {len(paths)} file(s)")
        return 0
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Cannot determine CI format scope: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
