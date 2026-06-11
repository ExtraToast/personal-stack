import test from "node:test";
import { loadFleet, readRepoText, assertContains } from "./_helpers.js";

test("k3s bootstrap module configures join token path and cluster firewall ports", async () => {
  const module = await readRepoText("platform/nix/modules/k3s/bootstrap.nix");
  assertContains(
    module,
    "apiServerEndpoint",
    "workerJoinTokenFile",
    "--token-file=",
    "allowedTCPPorts",
    "10250",
    "6443",
    "allowedUDPPorts = [ 8472 ]",
    "systemd.tmpfiles.rules",
    "preStart = lib.mkBefore",
    "ip -o -4 addr show dev tailscale0 scope global",
    "tailscale0 did not receive a global IPv4 address within 60s",
  );
});

test("worker and control plane profiles share the same k3s bootstrap defaults", async () => {
  const fleet = await loadFleet();
  const workerProfile = await readRepoText("platform/nix/profiles/worker.nix");
  const controlPlaneProfile = await readRepoText("platform/nix/profiles/control-plane.nix");
  const apiServerEndpoint = fleet.cluster.kubernetes.api_server_endpoint;
  const workerJoinTokenFile = fleet.cluster.kubernetes.worker_join_token_file;
  assertContains(workerProfile, "../modules/k3s/bootstrap.nix", `apiServerEndpoint = "${apiServerEndpoint}"`, `workerJoinTokenFile = "${workerJoinTokenFile}"`);
  assertContains(controlPlaneProfile, "../modules/k3s/bootstrap.nix", `apiServerEndpoint = "${apiServerEndpoint}"`, `workerJoinTokenFile = "${workerJoinTokenFile}"`);
});

test("bootstrap docs point workers at the token copy helper before deploy", async () => {
  const bootstrapReadme = await readRepoText("platform/cluster/bootstrap/README.md");
  const installPlaybook = await readRepoText("platform/cluster/bootstrap/home-install-playbook.md");
  const helperScript = await readRepoText("platform/scripts/bootstrap/bootstrap-k3s-worker.sh");
  assertContains(bootstrapReadme, "bootstrap-k3s-worker.sh");
  assertContains(installPlaybook, "bootstrap-k3s-worker.sh <node-name>", "deploy-host.sh <node-name>");
  assertContains(helperScript, "K3S_BOOTSTRAP_CONTROL_PLANE_NODE", "K3S_CONTROL_PLANE_TOKEN_FILE", "K3S_WORKER_JOIN_TOKEN_FILE", "platform_ssh_identity_file", "require_platform_ssh_identity_file_if_set", "sudo cat", "sudo tee");
});
