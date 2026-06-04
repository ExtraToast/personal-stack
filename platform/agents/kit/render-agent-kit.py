#!/usr/bin/env python3
"""Render checked-in agent kit templates into a repository tree."""

from __future__ import annotations

import argparse
import filecmp
import json
import os
import re
import shutil
import stat
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib import error, request


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
MANIFEST_PATH = KIT_ROOT / "manifest.yaml"
RUNNER_ENTRYPOINT = REPOSITORY_ROOT / "services" / "agent-runner" / "entrypoint.sh"
MCP_CONFIGMAP = (
    REPOSITORY_ROOT / "platform" / "cluster" / "flux" / "apps" / "agents" / "mcp" / "agents-mcp-servers-configmap.yaml"
)


@dataclass(frozen=True)
class RenderedFile:
    source: Path
    destination: Path
    relative_path: Path
    expand_includes: bool = False


@dataclass(frozen=True)
class RenderFindings:
    missing: list[RenderedFile]
    drifted: list[RenderedFile]


@dataclass(frozen=True)
class DoctorCheck:
    name: str
    status: str
    detail: str


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


def render_findings(destination_root: Path) -> RenderFindings:
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

    return RenderFindings(missing=missing, drifted=drifted)


def check(destination_root: Path) -> int:
    findings = render_findings(destination_root)

    if not findings.missing and not findings.drifted:
        print("agent kit render check passed")
        return 0

    print_render_findings(findings)
    return 1


def print_render_findings(findings: RenderFindings) -> None:
    for rendered in findings.missing:
        print(f"missing: {rendered.relative_path}", file=sys.stderr)
    for rendered in findings.drifted:
        print(f"drifted: {rendered.relative_path}", file=sys.stderr)


def manifest_version() -> str:
    if not MANIFEST_PATH.exists():
        return "unknown"
    match = re.search(r"^version:\s*([^\s#]+)", MANIFEST_PATH.read_text(), re.MULTILINE)
    return match.group(1) if match else "unknown"


def installed_version(manifest_path: Path) -> str | None:
    if not manifest_path.exists():
        return None
    match = re.search(r"^version=(.+)$", manifest_path.read_text(errors="replace"), re.MULTILINE)
    return match.group(1).strip() if match else ""


def agent_home_manifest_checks() -> list[DoctorCheck]:
    home = Path(os.environ.get("HOME", str(Path.home()))).expanduser()
    claude_home = Path(os.environ.get("CLAUDE_CONFIG_DIR", str(home / ".claude"))).expanduser()
    codex_home = Path(os.environ.get("CODEX_HOME", str(home / ".codex"))).expanduser()
    expected = os.environ.get("AGENT_KIT_EXPECTED_VERSION", "")
    checks: list[DoctorCheck] = []

    for agent, manifest in (
        ("claude-install", claude_home / ".knowledge-system-version"),
        ("codex-install", codex_home / ".knowledge-system-version"),
    ):
        version = installed_version(manifest)
        if version is None:
            checks.append(
                DoctorCheck(
                    name=agent,
                    status="warn",
                    detail=f"manifest missing at {manifest}; run /install.sh --agent all to refresh",
                ),
            )
        elif not version:
            checks.append(DoctorCheck(name=agent, status="warn", detail=f"manifest has no version at {manifest}"))
        elif expected and version != expected:
            checks.append(
                DoctorCheck(
                    name=agent,
                    status="warn",
                    detail=f"installed version {version}, expected {expected}; run /install.sh --agent all",
                ),
            )
        else:
            expected_detail = f", expected {expected}" if expected else ""
            checks.append(DoctorCheck(name=agent, status="ok", detail=f"manifest version {version}{expected_detail}"))

    return checks


def configmap_blocks(manifest: str) -> dict[str, str]:
    matches = list(re.finditer(r"(?m)^  (?P<key>[-A-Za-z0-9_.]+): \|$", manifest))
    blocks: dict[str, str] = {}
    for index, match in enumerate(matches):
        start = manifest.find("\n", match.end()) + 1
        end = matches[index + 1].start() if index + 1 < len(matches) else len(manifest)
        raw_block = manifest[start:end]
        lines: list[str] = []
        for line in raw_block.splitlines():
            if line.startswith("    "):
                lines.append(line[4:])
            elif not line.strip():
                lines.append("")
            else:
                break
        blocks[match.group("key")] = "\n".join(lines).strip()
    return blocks


def runner_profiles(entrypoint: str) -> set[str]:
    match = re.search(r"""case "\$AGENT_MCP_PROFILE" in\s+([-A-Za-z0-9_.|]+)\) ;;""", entrypoint)
    if not match:
        raise ValueError("AGENT_MCP_PROFILE allow-list not found in entrypoint")
    return set(match.group(1).split("|"))


def profile_names(blocks: dict[str, str], prefix: str, extension: str) -> set[str]:
    pattern = re.compile(rf"^{re.escape(prefix)}\.([-A-Za-z0-9_.]+)\.{re.escape(extension)}$")
    return {
        match.group(1)
        for key in blocks
        for match in [pattern.match(key)]
        if match
    }


def profile_counts(blocks: dict[str, str], profiles: set[str]) -> tuple[dict[str, int], dict[str, int]]:
    claude_counts: dict[str, int] = {}
    codex_counts: dict[str, int] = {}

    for profile in sorted(profiles):
        claude_key = f"claude-mcp-servers.{profile}.json"
        codex_key = f"codex-mcp-servers.{profile}.toml"
        try:
            claude_counts[profile] = len(json.loads(blocks[claude_key]))
        except KeyError as exc:
            raise ValueError(f"missing ConfigMap block {claude_key}") from exc
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSON in ConfigMap block {claude_key}: {exc.msg}") from exc

        try:
            codex_counts[profile] = len(re.findall(r"(?m)^\[mcp_servers\.", blocks[codex_key]))
        except KeyError as exc:
            raise ValueError(f"missing ConfigMap block {codex_key}") from exc
        if codex_counts[profile] == 0:
            raise ValueError(f"no Codex MCP servers found in ConfigMap block {codex_key}")

    return claude_counts, codex_counts


def mcp_profile_check() -> DoctorCheck:
    try:
        entrypoint_profiles = runner_profiles(RUNNER_ENTRYPOINT.read_text())
        blocks = configmap_blocks(MCP_CONFIGMAP.read_text())
        claude_profiles = profile_names(blocks, prefix="claude-mcp-servers", extension="json") - {""}
        codex_profiles = profile_names(blocks, prefix="codex-mcp-servers", extension="toml") - {""}
        profile_sets = {
            "entrypoint": entrypoint_profiles,
            "claude": claude_profiles,
            "codex": codex_profiles,
        }
        if len({tuple(sorted(value)) for value in profile_sets.values()}) != 1:
            details = "; ".join(f"{name}={','.join(sorted(value))}" for name, value in profile_sets.items())
            return DoctorCheck(name="mcp-profiles", status="fail", detail=f"profile sets differ: {details}")

        claude_counts, codex_counts = profile_counts(blocks, entrypoint_profiles)
        minimal = f"minimal {claude_counts.get('minimal', 0)} Claude/{codex_counts.get('minimal', 0)} Codex servers"
        full = (
            f"full-diagnostic {claude_counts.get('full-diagnostic', 0)} Claude/"
            f"{codex_counts.get('full-diagnostic', 0)} Codex servers"
        )
        return DoctorCheck(
            name="mcp-profiles",
            status="ok",
            detail=f"{len(entrypoint_profiles)} synchronized profiles; {minimal}; {full}",
        )
    except OSError as exc:
        return DoctorCheck(name="mcp-profiles", status="fail", detail=str(exc))
    except ValueError as exc:
        return DoctorCheck(name="mcp-profiles", status="fail", detail=str(exc))


def kb_reachability_check(require_live_kb: bool, timeout_seconds: float) -> DoctorCheck:
    kb_url = os.environ.get("KB_URL", "").rstrip("/")
    token = os.environ.get("KB_BEARER_TOKEN", "")

    if not kb_url:
        status = "fail" if require_live_kb else "warn"
        return DoctorCheck(name="kb-live", status=status, detail="KB_URL is not set; live MCP probe skipped")
    if not token:
        status = "fail" if require_live_kb else "warn"
        return DoctorCheck(name="kb-live", status=status, detail="KB_BEARER_TOKEN is not set; live MCP probe skipped")

    try:
        tools_body = mcp_post(
            kb_url=kb_url,
            token=token,
            payload={"jsonrpc": "2.0", "id": "agent-kit-doctor-tools", "method": "tools/list"},
            timeout_seconds=timeout_seconds,
        )
    except (OSError, error.URLError, json.JSONDecodeError) as exc:
        return DoctorCheck(name="kb-live", status="fail", detail=f"MCP tools/list probe failed: {exc}")

    if "error" in tools_body:
        return DoctorCheck(name="kb-live", status="fail", detail=f"MCP tools/list returned error: {tools_body['error']}")

    tool_names = {tool.get("name") for tool in tools_body.get("result", {}).get("tools", [])}
    if "knowledge.recall" not in tool_names:
        return DoctorCheck(name="kb-live", status="fail", detail="MCP tools/list did not include knowledge.recall")

    recall_payload = {
        "jsonrpc": "2.0",
        "id": "agent-kit-doctor-recall",
        "method": "tools/call",
        "params": {
            "name": "knowledge.recall",
            "arguments": {
                "query": "agent kit doctor reachability",
                "scope": "project:personal-stack",
                "mode": "fast",
                "limit": 1,
            },
        },
    }
    try:
        recall_body = mcp_post(kb_url=kb_url, token=token, payload=recall_payload, timeout_seconds=timeout_seconds)
    except (OSError, error.URLError, json.JSONDecodeError) as exc:
        return DoctorCheck(name="kb-live", status="fail", detail=f"MCP knowledge.recall probe failed: {exc}")

    if "error" in recall_body:
        return DoctorCheck(name="kb-live", status="fail", detail=f"MCP knowledge.recall returned error: {recall_body['error']}")

    structured = recall_body.get("result", {}).get("structuredContent", {})
    hits = structured.get("hits")
    hit_count = len(hits) if isinstance(hits, list) else 0
    return DoctorCheck(
        name="kb-live",
        status="ok",
        detail=f"reachable at {kb_url}/mcp with {len(tool_names)} tools; fast recall returned {hit_count} hits",
    )


def mcp_post(kb_url: str, token: str, payload: dict, timeout_seconds: float) -> dict:
    probe = request.Request(
        f"{kb_url}/mcp",
        data=json.dumps(payload).encode(),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with request.urlopen(probe, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode())


def doctor(args: argparse.Namespace) -> int:
    checks: list[DoctorCheck] = []
    findings = render_findings(REPOSITORY_ROOT)

    if not findings.missing and not findings.drifted:
        checks.append(DoctorCheck(name="render", status="ok", detail="generated files match templates"))
    else:
        details = []
        if findings.missing:
            details.append("missing " + ",".join(str(item.relative_path) for item in findings.missing))
        if findings.drifted:
            details.append("drifted " + ",".join(str(item.relative_path) for item in findings.drifted))
        checks.append(DoctorCheck(name="render", status="fail", detail="; ".join(details)))

    checks.append(DoctorCheck(name="manifest", status="ok", detail=f"kit manifest version {manifest_version()}"))
    checks.append(mcp_profile_check())
    checks.extend(agent_home_manifest_checks())
    checks.append(kb_reachability_check(require_live_kb=args.require_live_kb, timeout_seconds=args.kb_timeout_seconds))

    print("agent kit doctor")
    for item in checks:
        print(f"{item.status:<4} {item.name}: {item.detail}")

    failures = sum(1 for item in checks if item.status == "fail")
    warnings = sum(1 for item in checks if item.status == "warn")
    print(f"summary: {len(checks) - failures - warnings} ok, {warnings} warn, {failures} fail")
    if failures or (args.strict and warnings):
        return 1
    return 0


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
    mode.add_argument("--doctor", action="store_true", help="run read-only agent kit diagnostics")
    parser.add_argument("--strict", action="store_true", help="make doctor warnings fail")
    parser.add_argument("--require-live-kb", action="store_true", help="make doctor fail unless the KB MCP probe succeeds")
    parser.add_argument("--kb-timeout-seconds", type=float, default=5.0, help="timeout for the doctor KB MCP probe")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.check:
        return check(REPOSITORY_ROOT)
    if args.write:
        return render(REPOSITORY_ROOT)
    if args.doctor:
        return doctor(args)
    return render(args.output.resolve())


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
