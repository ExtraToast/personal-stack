import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { loadFleet, repoPath, readRepoText, assertContains } from './_helpers.js'

test('every inventory node has a nix host definition and disk layout', async () => {
  const fleet = await loadFleet()
  for (const nodeName of Object.keys(fleet.nodes)) {
    assert.ok(
      existsSync(repoPath('platform/nix/hosts', nodeName, 'default.nix')),
      `host ${nodeName} should define a default.nix`,
    )
    assert.ok(
      existsSync(repoPath('platform/nix/hosts', nodeName, 'disko.nix')),
      `host ${nodeName} should define a disko.nix`,
    )
  }
})

test('host definitions import profiles implied by fleet roles and capabilities', async () => {
  const fleet = await loadFleet()
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    const hostDefinition = await readRepoText('platform/nix/hosts', nodeName, 'default.nix')
    if (node.target_roles.includes('k3s-control-plane'))
      assertContains(hostDefinition, '../../profiles/control-plane.nix')
    if (node.target_roles.includes('k3s-worker')) assertContains(hostDefinition, '../../profiles/worker.nix')
    if (node.target_roles.includes('utility-host')) assertContains(hostDefinition, '../../profiles/utility.nix')
    if (node.capabilities.includes('nvidia')) assertContains(hostDefinition, '../../profiles/gpu-nvidia.nix')
  }
})

test('raspberry pi hosts override efi boot with generic extlinux', async () => {
  for (const nodeName of ['enschede-pi-1', 'enschede-pi-2', 'enschede-pi-3']) {
    const hostDefinition = await readRepoText('platform/nix/hosts', nodeName, 'default.nix')
    assertContains(
      hostDefinition,
      'imageBuild ? false',
      'lib.optional (!imageBuild) ./disko.nix',
      'systemd-boot.enable = lib.mkForce false',
      'efi.canTouchEfiVariables = lib.mkForce false',
      'generic-extlinux-compatible.enable = true',
    )
  }
})

test('flake exports host specific raspberry pi sd images', async () => {
  const flake = await readRepoText('platform/flake.nix')
  const buildScript = await readRepoText('platform/scripts/build/build-pi-image.sh')
  for (const nodeName of ['enschede-pi-1', 'enschede-pi-2', 'enschede-pi-3']) {
    assertContains(flake, `${nodeName} = mkHost {`, 'extraSpecialArgs = { imageBuild = true; };', 'piSdImages =')
    assertContains(buildScript, '#piSdImages.${NODE_NAME}')
  }
})

test('flake exports every inventory node with the correct architecture', async () => {
  const fleet = await loadFleet()
  const flake = await readRepoText('platform/flake.nix')
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    const expectedSystem = node.arch === 'amd64' ? 'x86_64-linux' : 'aarch64-linux'
    assertContains(
      flake,
      `${nodeName} = mkHost`,
      `${nodeName} = mkHost {`,
      `system = "${expectedSystem}";`,
      `hostModule = ./nix/hosts/${nodeName}/default.nix;`,
    )
  }
})

test('flake exports deploy targets for every ssh reachable inventory node', async () => {
  const fleet = await loadFleet()
  const flake = await readRepoText('platform/flake.nix')
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    if (!node.ssh) continue
    const expectedSystem = node.arch === 'amd64' ? 'x86_64-linux' : 'aarch64-linux'
    assertContains(
      flake,
      `deploy.nodes.${nodeName}`,
      `hostname = "${node.ssh.host}"`,
      `sshUser = "${node.ssh.user}"`,
      `sshOpts = [ "-p" "${node.ssh.port}" ]`,
      `deploy-rs.lib.${expectedSystem}.activate.nixos self.nixosConfigurations.${nodeName}`,
    )
  }
})

test('gtx 960m host runs with the nvidia driver unloaded', async () => {
  const fleet = await loadFleet()
  const hostDefinition = await readRepoText('platform/nix/hosts/enschede-gtx-960m-1/default.nix')
  const gtxNode = fleet.nodes['enschede-gtx-960m-1']

  // Maxwell cannot runtime-suspend (Runtime D3 needs Turing or newer), so the
  // card idles at full power for as long as a driver is bound to it. The host
  // therefore claims no GPU capability at all.
  assert.ok(!gtxNode.capabilities.includes('nvidia'))
  assert.ok(!gtxNode.capabilities.includes('game-streaming'))
  assert.equal(gtxNode.gpus, undefined)
  assert.ok(!fleet.service_intent.host_native['enschede-gtx-960m-1'].includes('game-streaming'))
  assert.ok(!('temporary_gpu_model' in fleet.placement_intent.gpu_specific.jellyfin))

  assert.ok(!hostDefinition.includes('profiles/gpu-nvidia.nix'))
  assert.ok(!hostDefinition.includes('modules/services/game-streaming.nix'))
  assert.ok(!hostDefinition.includes('capability-nvidia'))
  assert.ok(!hostDefinition.includes('capability-game-streaming'))
  assert.ok(!hostDefinition.includes('gpu-model-gtx960m'))
  assertContains(
    hostDefinition,
    'boot.blacklistedKernelModules',
    '"nouveau"',
    '"nvidia"',
    '"nvidia_drm"',
    '"nvidia_modeset"',
    '"nvidia_uvm"',
    'ATTR{vendor}=="0x10de"',
    'ATTR{power/control}="auto"',
  )
})

test('rx7900xtx host imports game streaming service', async () => {
  const fleet = await loadFleet()
  const flake = await readRepoText('platform/flake.nix')
  const hostDefinition = await readRepoText('platform/nix/hosts/enschede-rx7900xtx-1/default.nix')
  const module = await readRepoText('platform/nix/modules/services/game-streaming-amd.nix')
  const amdNode = fleet.nodes['enschede-rx7900xtx-1']
  assert.ok(amdNode.capabilities.includes('game-streaming'))
  assert.ok(fleet.service_intent.host_native['enschede-rx7900xtx-1'].includes('game-streaming'))
  assertContains(flake, 'deploy.nodes.enschede-rx7900xtx-1')
  assertContains(
    hostDefinition,
    '../../modules/services/game-streaming-amd.nix',
    '"personal-stack/capability-game-streaming" = "true"',
  )
  assertContains(
    module,
    'ghcr.io/games-on-whales/wolf:stable',
    'ghcr.io/games-on-whales/retroarch:edge',
    'ghcr.io/games-on-whales/es-de:edge',
    'ghcr.io/games-on-whales/xfce:edge',
    'ghcr.io/games-on-whales/steam:edge',
    'ghcr.io/games-on-whales/heroic-games-launcher:edge',
    'ghcr.io/games-on-whales/lutris:edge',
    'Wolf UI',
    'title = "Steam"',
    'title = "Heroic"',
    'title = "Lutris"',
    'STEAM_STARTUP_FLAGS=-bigpicture',
    'WINEPREFIX=/home/retro/Games/Prefixes/default',
    'virtualisation.docker',
    'virtualisation.oci-containers',
    'WOLF_RENDER_NODE = "/dev/dri/renderD128"',
    'WOLF_SOCKET_PATH = "/var/run/wolf/wolf.sock"',
    '"/run/wolf:/var/run/wolf:rw"',
    'd /var/lib/personal-stack/wolfmanager/config',
    '--network=host',
    '--device=/dev/uinput',
    '--device=/dev/uhid',
    'hardware.uinput.enable = true',
    'boot.kernelModules',
    'services.pipewire',
    'uid = 1001',
    'localGamesMount = "/srv/game-streaming"',
    'wolf-config-seed',
    'wolf-config-reconcile',
    'append_app Steam',
    'append_app Heroic',
    'append_app Lutris',
    '47984',
    '47989',
    '47990',
    '48010',
    'from = 8000',
    'to = 8010',
  )
})

test('gpu nvidia profile still covers the t1000 transcode host', async () => {
  const gpuProfile = await readRepoText('platform/nix/profiles/gpu-nvidia.nix')
  const t1000 = await readRepoText('platform/nix/hosts/enschede-t1000-1/default.nix')
  assertContains(t1000, '../../profiles/gpu-nvidia.nix', '"personal-stack/capability-nvidia" = "true"')
  assertContains(
    gpuProfile,
    'lib.hasPrefix "nvidia-" name',
    'lib.hasPrefix "cuda" name',
    'lib.hasPrefix "libcu" name',
    'lib.hasPrefix "libn" name',
    'lib.hasPrefix "libnv" name',
    'CUDA EULA',
    'lib.hasPrefix "libretro-" name',
  )
})
