{ ... }:
{
  imports = [
    ../modules/k3s/bootstrap.nix
    ../modules/roles/control-plane.nix
  ];

  personalStack.k3sBootstrap = {
    apiServerEndpoint = "https://167.86.79.203:6443";
    workerJoinTokenFile = "/var/lib/personal-stack/secrets/k3s/agent-token";
  };
}
