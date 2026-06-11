import test from "node:test";
import assert from "node:assert/strict";
import { loadFleet, parseFleetText } from "./_helpers.js";

test("loads the seeded platform inventory", async () => {
  const fleet = await loadFleet();

  assert.equal(fleet.cluster.name, "personal-stack");
  assert.equal(fleet.cluster.kubernetes.bootstrap_control_plane, "frankfurt-contabo-1");
  assert.equal(fleet.cluster.kubernetes.api_server_endpoint, "https://167.86.79.203:6443");
  assert.equal(fleet.cluster.kubernetes.control_plane_token_file, "/var/lib/rancher/k3s/server/node-token");
  assert.equal(fleet.cluster.kubernetes.worker_join_token_file, "/var/lib/personal-stack/secrets/k3s/agent-token");
  assert.deepEqual(Object.keys(fleet.sites).sort(), ["enschede", "frankfurt"]);
  assert.equal(fleet.sites.enschede.networking.lan_ingress_ip, "192.168.0.99");
  assert.equal(fleet.sites.enschede.networking.wan_public_ip, "130.89.174.190");
  assert.equal(fleet.ingress_intent.wan_origin_overrides.jellyfin, "home_direct");
  for (const node of [
    "frankfurt-contabo-1",
    "enschede-gtx-960m-1",
    "enschede-t1000-1",
    "enschede-rx7900xtx-1",
    "enschede-pi-1",
    "enschede-pi-2",
    "enschede-pi-3",
  ]) assert.ok(node in fleet.nodes);
  assert.equal(fleet.nodes["frankfurt-contabo-1"].ssh.host, "167.86.79.203");
  assert.equal(fleet.nodes["enschede-t1000-1"].ssh.user, "deploy");
  assert.equal(fleet.nodes["enschede-pi-1"].ssh.host, "100.65.192.22");
  assert.deepEqual([...new Set(Object.values(fleet.nodes).map((node) => node.ssh?.port).filter(Boolean))], [2222]);
  assert.equal(fleet.placement_intent.gpu_specific.jellyfin.preferred_gpu_model, "t1000");
  assert.deepEqual(fleet.exposure_intent.public_and_lan.sort(), [
    "assistant-ws",
    "bazarr",
    "immich",
    "jellyfin",
    "jellyseerr",
    "prowlarr",
    "qbittorrent",
    "radarr",
    "sonarr",
  ]);
  assert.equal(fleet.ingress_intent.kubernetes_backends.vault.port, 8200);
  assert.equal(fleet.ingress_intent.kubernetes_backends["auth-api"].namespace, "auth-system");
  assert.equal(fleet.ingress_intent.kubernetes_backends["assistant-api"].port, 8082);
  assert.equal(fleet.ingress_intent.kubernetes_backends.stalwart.namespace, "mail-system");
  assert.equal(fleet.ingress_intent.kubernetes_backends.stalwart.port, 8080);
  assert.equal(fleet.ingress_intent.kubernetes_backends.bazarr.port, 6767);
  assert.equal(fleet.ingress_intent.kubernetes_backends.jellyseerr.port, 5055);
  assert.equal(fleet.ingress_intent.kubernetes_backends.prowlarr.port, 9696);
  assert.equal(fleet.ingress_intent.kubernetes_backends.qbittorrent.port, 8080);
  assert.ok(!("headscale" in fleet.ingress_intent.kubernetes_backends));
});

test("rejects active nodes without ssh connection details", () => {
  assert.throws(() => parseFleetText(`
version: 1
cluster:
  name: personal-stack
  public_domain: jorisjonkers.dev
  kubernetes:
    bootstrap_control_plane: frankfurt-contabo-1
    api_server_endpoint: https://167.86.79.203:6443
    control_plane_token_file: /var/lib/rancher/k3s/server/node-token
    worker_join_token_file: /var/lib/personal-stack/secrets/k3s/agent-token
sites:
  frankfurt:
    kind: vps
    purpose: primary_cluster_site
nodes:
  frankfurt-contabo-1:
    status: active
    site: frankfurt
    arch: amd64
    target_roles: [k3s-control-plane]
    capacity: {cpu_millicores: 1000, memory_mib: 1024}
    capabilities: [tailscale]
service_intent:
  kubernetes: {public_apps: [], internal_platform: [], home_media: []}
  host_native: {}
placement_intent: {frankfurt_only: [], enschede_only: [], gpu_specific: {}}
exposure_intent: {public: [], public_and_lan: [], internal_only: [], lan_only: []}
`), /active node frankfurt-contabo-1 must define ssh connection details/);
});

test("rejects unknown site references", () => {
  assert.throws(() => parseFleetText(`
version: 1
cluster:
  name: personal-stack
  public_domain: jorisjonkers.dev
  kubernetes:
    bootstrap_control_plane: stray-node
    api_server_endpoint: https://167.86.79.203:6443
    control_plane_token_file: /var/lib/rancher/k3s/server/node-token
    worker_join_token_file: /var/lib/personal-stack/secrets/k3s/agent-token
sites:
  frankfurt:
    kind: vps
    purpose: primary_cluster_site
nodes:
  stray-node:
    status: planned
    site: enschede
    arch: arm64
    target_roles: [k3s-control-plane, k3s-worker]
    capacity: {cpu_millicores: 1000, memory_mib: 1024}
    capabilities: [tailscale]
service_intent:
  kubernetes: {public_apps: [], internal_platform: [], home_media: []}
  host_native: {}
placement_intent: {frankfurt_only: [], enschede_only: [], gpu_specific: {}}
exposure_intent: {public: [], public_and_lan: [], internal_only: [], lan_only: []}
`), /node stray-node references unknown site enschede/);
});

test("rejects externally exposed kubernetes services without ingress backends", () => {
  assert.throws(() => parseFleetText(`
version: 1
cluster:
  name: personal-stack
  public_domain: jorisjonkers.dev
  kubernetes:
    bootstrap_control_plane: frankfurt-contabo-1
    api_server_endpoint: https://167.86.79.203:6443
    control_plane_token_file: /var/lib/rancher/k3s/server/node-token
    worker_join_token_file: /var/lib/personal-stack/secrets/k3s/agent-token
sites:
  frankfurt: {kind: vps, purpose: primary_cluster_site}
nodes:
  frankfurt-contabo-1:
    status: active
    site: frankfurt
    arch: amd64
    ssh: {host: 167.86.79.203, user: deploy, port: 2222}
    target_roles: [k3s-control-plane]
    capacity: {cpu_millicores: 1000, memory_mib: 1024}
    capabilities: [tailscale]
service_intent:
  kubernetes: {public_apps: [app-ui], internal_platform: [], home_media: []}
  host_native: {}
placement_intent: {frankfurt_only: [app-ui], enschede_only: [], gpu_specific: {}}
exposure_intent: {public: [app-ui], public_and_lan: [], internal_only: [], lan_only: []}
access_intent:
  host_labels: {app-ui: root}
ingress_intent:
  kubernetes_backends: {}
`), /externally exposed kubernetes service app-ui must declare an ingress backend/);
});
