{ ... }:
{
  imports = [
    ../modules/base/default.nix
    ../modules/k3s/bootstrap.nix
    ../modules/roles/worker.nix
    ../modules/services/tailscale.nix
  ];

  personalStack.k3sBootstrap = {
    apiServerEndpoint = "https://167.86.79.203:6443";
    workerJoinTokenFile = "/var/lib/personal-stack/secrets/k3s/agent-token";
  };
}
