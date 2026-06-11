import test from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { repoPath } from "./_helpers.js";

test("platform scaffold exists for nix and flux bootstrap", () => {
  for (const file of [
    "platform/flake.nix",
    "platform/nix/profiles/control-plane.nix",
    "platform/nix/profiles/worker.nix",
    "platform/nix/profiles/utility.nix",
    "platform/nix/profiles/gpu-nvidia.nix",
    "platform/nix/modules/base/default.nix",
    "platform/nix/modules/services/game-streaming.nix",
    "platform/nix/authorized-keys/README.md",
    "platform/nix/modules/image/raspberry-pi-sd-image.nix",
    "platform/nix/hosts/frankfurt-contabo-1/default.nix",
    "platform/nix/hosts/frankfurt-contabo-1/disko.nix",
    "platform/scripts/install/install-host.sh",
    "platform/scripts/deploy/deploy-host.sh",
    "platform/scripts/bootstrap/bootstrap-tailnet.sh",
    "platform/scripts/bootstrap/bootstrap-k3s-worker.sh",
    "platform/scripts/build/build-pi-image.sh",
    "platform/scripts/game-streaming/copy-t1000-emulation-to-gtx.sh",
    "platform/cluster/bootstrap/game-streaming-playbook.md",
    "platform/cluster/flux/clusters/production/kustomization.yaml",
  ]) assert.ok(existsSync(repoPath(file)), `${file} should exist`);
});
