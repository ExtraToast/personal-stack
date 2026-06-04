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
