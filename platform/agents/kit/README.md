# Personal-stack agent kit

This directory is the checked-in control plane for cross-agent memory,
hook, and skill parity. The first version is a manifest plus tests; later
PRs should move the hook and skill bodies into templates rendered from
this kit.

The manifest intentionally records today's gaps. A Claude-only installer
hook or skill must carry an explicit `unsupported_reason` or `follow_up`
so drift is visible instead of accidental.

Run the guard with:

```bash
./gradlew :platform:tooling:test --tests com.jorisjonkers.personalstack.platform.AgentKitManifestTest
```
