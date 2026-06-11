import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import { readFile } from "node:fs/promises";
import { repoPath, repoRoot, runProcess, tempDir, writeExecutable } from "./_helpers.js";

test("build-pi-image targets the host specific sd image output", async () => {
  const dir = await tempDir();
  const nixLog = path.join(dir, "nix-pi-image.log");
  const inventoryStub = await writeExecutable(path.join(dir, "inventory-pi-image"), `
#!/usr/bin/env bash
cat <<'EOF'
NODE_NAME=enschede-pi-1
NODE_STATUS=install-ready
NODE_SITE=enschede
NODE_ARCH=arm64
NIX_SYSTEM=aarch64-linux
HAS_SSH=true
SSH_HOST=enschede-pi-1
SSH_USER=deploy
SSH_PORT=2222
EOF
`);
  const nixStub = await writeExecutable(path.join(dir, "nix-pi-image-stub"), `
#!/usr/bin/env bash
printf '%s\\n' "$@" > "${nixLog}"
`);

  const result = await runProcess(repoPath("platform/scripts/build/build-pi-image.sh"), ["enschede-pi-1"], {
    env: {
      DEPLOY_CONFIG_SCHEMA_BIN: inventoryStub,
      PLATFORM_NIX: nixStub,
      PLATFORM_CURRENT_SYSTEM: "aarch64-linux",
    },
  });

  assert.equal(result.exitCode, 0, result.stderr);
  assert.deepEqual((await readFile(nixLog, "utf8")).trimEnd().split("\n"), [
    "--extra-experimental-features",
    "nix-command flakes",
    "build",
    `path:${repoPath("platform")}#piSdImages.enschede-pi-1`,
    "--out-link",
    "result-enschede-pi-1-sd-image",
    "--print-build-logs",
  ]);
});

test("build-pi-image rejects non arm hosts", async () => {
  const dir = await tempDir();
  const inventoryStub = await writeExecutable(path.join(dir, "inventory-non-pi-image"), `
#!/usr/bin/env bash
cat <<'EOF'
NODE_NAME=frankfurt-contabo-1
NODE_STATUS=active
NODE_SITE=frankfurt
NODE_ARCH=amd64
NIX_SYSTEM=x86_64-linux
HAS_SSH=true
SSH_HOST=167.86.79.203
SSH_USER=deploy
SSH_PORT=2222
EOF
`);
  const nixStub = await writeExecutable(path.join(dir, "nix-non-pi-image-stub"), `
#!/usr/bin/env bash
echo should-not-run >&2
exit 99
`);
  const result = await runProcess(repoPath("platform/scripts/build/build-pi-image.sh"), ["frankfurt-contabo-1"], {
    cwd: repoRoot,
    env: { DEPLOY_CONFIG_SCHEMA_BIN: inventoryStub, PLATFORM_NIX: nixStub },
  });
  assert.equal(result.exitCode, 1);
  assert.match(result.stderr, /not an arm64 Raspberry Pi image target/);
});
