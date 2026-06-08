# Personal-stack agent kit

This directory is the checked-in control plane for cross-agent memory,
hook, and skill parity. The current renderer owns repo skills, repo hook
files, hook settings, and the installer entrypoint. Installer heredoc
bodies, including hooks, skills, settings, and allowlists, are rendered
from partial templates under
`templates/installer/partials`.

The manifest intentionally records today's gaps. A Claude-only installer
hook or skill must carry an explicit `unsupported_reason` or `follow_up`
so drift is visible instead of accidental.

Run the guard with:

```bash
./gradlew :platform:tooling:test --tests com.jorisjonkers.personalstack.platform.AgentKitManifestTest
```

Render or check the repo templates with:

```bash
platform/agents/kit/render-agent-kit.py --check
platform/agents/kit/render-agent-kit.py --output /tmp/agent-kit-render
```

Use `--write` only when intentionally updating checked-in repo mirrors or
installer resources from `platform/agents/kit/templates`.

Install the generated client kit through the served installer:

```bash
curl -fsSL -H "Authorization: Bearer ${KB_BEARER_TOKEN}" \
  "${KB_URL}/install.sh" | bash -s -- --agent all --scope user
```

Use `--scope project` with `AGENT_KIT_PROJECT_ROOT` when the kit should write
repo-local `.claude` and `.codex` directories instead of user config homes.
See [PORTABILITY.md](PORTABILITY.md) for uninstall, backup/export, restore, and
compatibility-matrix details.

## Spec-driven development scaffold

The kit vendors the GitHub Spec Kit runtime under
`templates/repo/.specify`. The checked-in scaffold includes templates plus the
Bash helper scripts under `.specify/scripts/bash`; those scripts are executable
in the template tree and are installed with mode `0755`.

`render-agent-kit.py` treats `.specify` as a project seed, not as generated
client state:

- The personal-stack repo-root `.specify` tree is committed. Its scripts and
  templates mirror the vendored scaffold; its memory directory carries the real
  project constitution.
- The repo-local `.specify/memory/constitution.md` is committed and hand-edited.
  It is the personal-stack constitution. Do not render over it; edit that file
  directly when governance changes.
- The generic `.specify/templates/constitution-template.md` remains a scaffold
  template for future projects.
- Project-scope installs seed `.specify` under `AGENT_KIT_PROJECT_ROOT` or
  `$PWD`. The constitution is written only when absent; the vendored scripts and
  templates are refreshed from the installer bundle.
- `.specify` is intentionally not tracked for uninstall. `install.sh
  --uninstall` removes agent client files, but never removes a project's Spec
  Kit scaffold or specs.
- Runner workspaces are also seeded from the same scaffold. The agent runner
  image copies `templates/repo/.specify` to `/opt/agent-kit/sdd`, and the
  runner entrypoint seeds workspace repositories from `AGENT_KIT_SDD_SOURCE`.

Spec Kit commands are dot-form commands: `/speckit.specify`,
`/speckit.clarify`, `/speckit.plan`, `/speckit.tasks`, `/speckit.analyze`,
`/speckit.implement`, `/speckit.checklist`, `/speckit.constitution`, and
`/speckit.taskstoissues`. They ship through `install.sh` for both `--scope user`
and `--scope project`. Claude receives slash-command Markdown under
`commands/speckit.*.md`; Codex receives matching `speckit-*` skills. The
user-scope `/speckit.specify` flow bootstraps a minimal `.specify` tree in the
current project when it is absent and does not overwrite existing Spec Kit files.

Run the read-only doctor when debugging runner memory, MCP, or installer
drift:

```bash
platform/agents/kit/render-agent-kit.py --doctor
platform/agents/kit/render-agent-kit.py --doctor --require-live-kb
```

By default, doctor fails static repo drift and MCP profile mismatches. Missing
local Claude/Codex installs and skipped live KB probes are warnings so the
command remains portable in CI and fresh clones. Use `--strict` to make
warnings fail, and use `--require-live-kb` when `KB_URL` and
`KB_BEARER_TOKEN` must be present, the MCP server must answer `tools/list`,
and `knowledge.recall(mode=fast, limit=1)` must complete.
