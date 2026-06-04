#!/usr/bin/env python3
"""Render checked-in agent kit templates into a repository tree."""

from __future__ import annotations

import argparse
import filecmp
import re
import shutil
import stat
import sys
from dataclasses import dataclass
from pathlib import Path


KIT_ROOT = Path(__file__).resolve().parent
REPOSITORY_ROOT = KIT_ROOT.parents[2]
REPO_TEMPLATE_ROOT = KIT_ROOT / "templates" / "repo"
EXTRA_TEMPLATES = (
    (
        KIT_ROOT / "templates" / "installer" / "install.sh.tpl",
        Path("services/knowledge-api/src/main/resources/installer/install.sh"),
        True,
    ),
)
INCLUDE_PATTERN = re.compile(r"^# @agent-kit-include (?P<path>[A-Za-z0-9_./-]+)$")


@dataclass(frozen=True)
class RenderedFile:
    source: Path
    destination: Path
    relative_path: Path
    expand_includes: bool = False


def template_files(destination_root: Path) -> list[RenderedFile]:
    if not REPO_TEMPLATE_ROOT.is_dir():
        raise FileNotFoundError(f"template root does not exist: {REPO_TEMPLATE_ROOT}")

    files: list[RenderedFile] = []
    for source in sorted(REPO_TEMPLATE_ROOT.rglob("*")):
        if not source.is_file():
            continue
        relative_path = source.relative_to(REPO_TEMPLATE_ROOT)
        files.append(
            RenderedFile(
                source=source,
                destination=destination_root / relative_path,
                relative_path=relative_path,
            ),
        )

    for source, relative_path, expand_includes in EXTRA_TEMPLATES:
        if not source.is_file():
            raise FileNotFoundError(f"template file does not exist: {source}")
        files.append(
            RenderedFile(
                source=source,
                destination=destination_root / relative_path,
                relative_path=relative_path,
                expand_includes=expand_includes,
            ),
        )
    return files


def rendered_content(rendered: RenderedFile) -> bytes:
    if not rendered.expand_includes:
        return rendered.source.read_bytes()

    parts: list[str] = []
    template_root = rendered.source.parent
    for raw_line in rendered.source.read_text().splitlines(keepends=True):
        line = raw_line.rstrip("\r\n")
        match = INCLUDE_PATTERN.match(line)
        if not match:
            parts.append(raw_line)
            continue

        include_path = (template_root / match.group("path")).resolve()
        if not include_path.is_relative_to(template_root.resolve()):
            raise ValueError(f"include escapes template root: {include_path}")
        if not include_path.is_file():
            raise FileNotFoundError(f"include file does not exist: {include_path}")

        content = include_path.read_text()
        parts.append(content)
        if not content.endswith("\n"):
            parts.append("\n")

    return "".join(parts).encode()


def check(destination_root: Path) -> int:
    drifted: list[RenderedFile] = []
    missing: list[RenderedFile] = []

    for rendered in template_files(destination_root):
        if not rendered.destination.exists():
            missing.append(rendered)
        elif rendered.expand_includes:
            if rendered_content(rendered) != rendered.destination.read_bytes():
                drifted.append(rendered)
        elif not filecmp.cmp(rendered.source, rendered.destination, shallow=False):
            drifted.append(rendered)

    if not missing and not drifted:
        print("agent kit render check passed")
        return 0

    for rendered in missing:
        print(f"missing: {rendered.relative_path}", file=sys.stderr)
    for rendered in drifted:
        print(f"drifted: {rendered.relative_path}", file=sys.stderr)
    return 1


def render(destination_root: Path) -> int:
    for rendered in template_files(destination_root):
        rendered.destination.parent.mkdir(parents=True, exist_ok=True)
        if rendered.expand_includes:
            rendered.destination.write_bytes(rendered_content(rendered))
        else:
            shutil.copyfile(rendered.source, rendered.destination)
        source_mode = rendered.source.stat().st_mode
        executable = source_mode & stat.S_IXUSR
        mode = 0o755 if executable else 0o644
        rendered.destination.chmod(mode)
    print(f"rendered {len(template_files(destination_root))} agent kit files into {destination_root}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="verify templates match the repository tree")
    mode.add_argument("--write", action="store_true", help="render templates into the repository tree")
    mode.add_argument("--output", type=Path, help="render templates into a separate output directory")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.check:
        return check(REPOSITORY_ROOT)
    if args.write:
        return render(REPOSITORY_ROOT)
    return render(args.output.resolve())


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
