import test from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { loadFleet, repoPath, readRepoText, assertContains } from "./_helpers.js";

test("every inventory node has a nix host definition and disk layout", async () => {
  const fleet = await loadFleet();
  for (const nodeName of Object.keys(fleet.nodes)) {
    assert.ok(existsSync(repoPath("platform/nix/hosts", nodeName, "default.nix")), `host ${nodeName} should define a default.nix`);
    assert.ok(existsSync(repoPath("platform/nix/hosts", nodeName, "disko.nix")), `host ${nodeName} should define a disko.nix`);
  }
});

test("host definitions import profiles implied by fleet roles and capabilities", async () => {
  const fleet = await loadFleet();
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    const hostDefinition = await readRepoText("platform/nix/hosts", nodeName, "default.nix");
    if (node.target_roles.includes("k3s-control-plane")) assertContains(hostDefinition, "../../profiles/control-plane.nix");
    if (node.target_roles.includes("k3s-worker")) assertContains(hostDefinition, "../../profiles/worker.nix");
    if (node.target_roles.includes("utility-host")) assertContains(hostDefinition, "../../profiles/utility.nix");
    if (node.capabilities.includes("nvidia")) assertContains(hostDefinition, "../../profiles/gpu-nvidia.nix");
  }
});

test("raspberry pi hosts override efi boot with generic extlinux", async () => {
  for (const nodeName of ["enschede-pi-1", "enschede-pi-2", "enschede-pi-3"]) {
    const hostDefinition = await readRepoText("platform/nix/hosts", nodeName, "default.nix");
    assertContains(
      hostDefinition,
      "imageBuild ? false",
      "lib.optional (!imageBuild) ./disko.nix",
      "systemd-boot.enable = lib.mkForce false",
      "efi.canTouchEfiVariables = lib.mkForce false",
      "generic-extlinux-compatible.enable = true",
    );
  }
});

test("flake exports host specific raspberry pi sd images", async () => {
  const flake = await readRepoText("platform/flake.nix");
  const buildScript = await readRepoText("platform/scripts/build/build-pi-image.sh");
  for (const nodeName of ["enschede-pi-1", "enschede-pi-2", "enschede-pi-3"]) {
    assertContains(flake, `${nodeName} = mkHost {`, "extraSpecialArgs = { imageBuild = true; };", "piSdImages =");
    assertContains(buildScript, '#piSdImages.${NODE_NAME}');
  }
});

test("flake exports every inventory node with the correct architecture", async () => {
  const fleet = await loadFleet();
  const flake = await readRepoText("platform/flake.nix");
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    const expectedSystem = node.arch === "amd64" ? "x86_64-linux" : "aarch64-linux";
    assertContains(flake, `${nodeName} = mkHost`, `${nodeName} = mkHost {`, `system = "${expectedSystem}";`, `hostModule = ./nix/hosts/${nodeName}/default.nix;`);
  }
});

test("flake exports deploy targets for every ssh reachable inventory node", async () => {
  const fleet = await loadFleet();
  const flake = await readRepoText("platform/flake.nix");
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    if (!node.ssh) continue;
    const expectedSystem = node.arch === "amd64" ? "x86_64-linux" : "aarch64-linux";
    assertContains(
      flake,
      `deploy.nodes.${nodeName}`,
      `hostname = "${node.ssh.host}"`,
      `sshUser = "${node.ssh.user}"`,
      `sshOpts = [ "-p" "${node.ssh.port}" ]`,
      `deploy-rs.lib.${expectedSystem}.activate.nixos self.nixosConfigurations.${nodeName}`,
    );
  }
});

test("gtx 960m host imports game streaming service", async () => {
  const fleet = await loadFleet();
  const flake = await readRepoText("platform/flake.nix");
  const hostDefinition = await readRepoText("platform/nix/hosts/enschede-gtx-960m-1/default.nix");
  const module = await readRepoText("platform/nix/modules/services/game-streaming.nix");
  const gpuProfile = await readRepoText("platform/nix/profiles/gpu-nvidia.nix");
  const gtxNode = fleet.nodes["enschede-gtx-960m-1"];
  assert.ok(gtxNode.capabilities.includes("game-streaming"));
  assert.ok(gtxNode.capabilities.includes("nvidia"));
  assert.ok(fleet.service_intent.host_native["enschede-gtx-960m-1"].includes("game-streaming"));
  assertContains(flake, "deploy.nodes.enschede-gtx-960m-1", "magicRollback = false");
  assertContains(hostDefinition, "../../modules/services/game-streaming.nix", "\"personal-stack/capability-game-streaming\" = \"true\"");
  assertContains(
    module,
    "ghcr.io/games-on-whales/wolf:stable",
    "ghcr.io/games-on-whales/retroarch:edge",
    "ghcr.io/games-on-whales/es-de:edge",
    "ghcr.io/games-on-whales/xfce:edge",
    "ghcr.io/games-on-whales/steam:edge",
    "ghcr.io/games-on-whales/heroic-games-launcher:edge",
    "ghcr.io/games-on-whales/lutris:edge",
    "support_hevc = false",
    "Wolf UI",
    "title = \"Steam\"",
    "title = \"Heroic\"",
    "title = \"Lutris\"",
    "STEAM_STARTUP_FLAGS=-bigpicture",
    "WINEPREFIX=/home/retro/Games/Prefixes/default",
    "4K60",
    "virtualisation.docker",
    "virtualisation.oci-containers",
    "WOLF_RENDER_NODE = \"/dev/dri/renderD129\"",
    "WOLF_SOCKET_PATH = \"/var/run/wolf/wolf.sock\"",
    "\"/run/wolf:/var/run/wolf:rw\"",
    "d /var/lib/personal-stack/wolfmanager/config",
    "\"DeviceRequests\"",
    "--device=nvidia.com/gpu=all",
    "--network=host",
    "--device=/dev/uinput",
    "--device=/dev/uhid",
    "hardware.uinput.enable = true",
    "nvidia-drm.modeset=1",
    "boot.kernelModules",
    "services.pipewire",
    "uid = 1001",
    "localGamesMount = \"/srv/game-streaming\"",
    'localRomsMount = "${localGamesMount}/roms"',
    "device = \"/dev/disk/by-uuid/1120-414D\"",
    "fsType = \"vfat\"",
    'fileSystems.${gamesMount}',
    "wolf-config-seed",
    "wolf-config-reconcile",
    "config.toml.pre-store-launchers",
    "append_app Steam",
    "append_app Heroic",
    "append_app Lutris",
    '${wolfState}/cfg/config.toml',
    '${gamesMount}:/ROMs:ro',
    '${localRomsMount}:/ROMs-local:ro',
    '${localGamesMount}:/games-local:ro',
    'd ${localGamesMount}/imports/t1000',
    '${pcGamesMount}/steam:/home/retro/Games/SteamLibrary:rw',
    '${pcGamesMount}/heroic:/home/retro/Games/Heroic:rw',
    '${pcGamesMount}/lutris:/home/retro/Games/Lutris:rw',
    '${pcGamesMount}/prefixes:/home/retro/Games/Prefixes:rw',
    '${pcGamesMount}/downloads:/home/retro/Downloads:rw',
    "47984",
    "47989",
    "47990",
    "48010",
    "from = 8000",
    "to = 8010",
    "hardware.nvidia-container-toolkit.enable",
  );
  assertContains(
    gpuProfile,
    "lib.hasPrefix \"nvidia-\" name",
    "lib.hasPrefix \"cuda\" name",
    "lib.hasPrefix \"libcu\" name",
    "lib.hasPrefix \"libn\" name",
    "lib.hasPrefix \"libnv\" name",
    "CUDA EULA",
    "lib.hasPrefix \"libretro-\" name",
  );
});
