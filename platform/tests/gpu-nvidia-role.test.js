import test from "node:test";
import { readRepoText, assertContains } from "./_helpers.js";

test("gpu nvidia profile allows unfree nvidia packages", async () => {
  const profile = await readRepoText("platform/nix/profiles/gpu-nvidia.nix");
  assertContains(profile, "nixpkgs.config.allowUnfreePredicate", "lib.hasPrefix \"nvidia-\" name");
});

test("gpu nvidia role enables driver modesetting and container toolkit", async () => {
  const role = await readRepoText("platform/nix/modules/roles/gpu-nvidia.nix");
  assertContains(
    role,
    "services.xserver.videoDrivers = [ \"nvidia\" ];",
    "hardware.graphics.enable = true;",
    "hardware.nvidia = {",
    "open = false;",
    "modesetting.enable = true;",
    "package = lib.mkDefault config.boot.kernelPackages.nvidiaPackages.stable;",
    "hardware.nvidia-container-toolkit.enable = true;",
    "libva",
    "pciutils",
  );
});
