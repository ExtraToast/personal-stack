{ config, lib, pkgs, ... }:
let
  cfg = config.personalStack.k3sBootstrap;
  tokenDirectory = builtins.dirOf cfg.workerJoinTokenFile;
  isK3sAgent = config.services.k3s.enable && config.services.k3s.role == "agent";
  isK3sServer = config.services.k3s.enable && config.services.k3s.role == "server";
in
{
  options.personalStack.k3sBootstrap = {
    apiServerEndpoint = lib.mkOption {
      type = lib.types.str;
      default = "";
      description = "Bootstrap k3s API server endpoint used by worker nodes during cluster join.";
    };

    workerJoinTokenFile = lib.mkOption {
      type = lib.types.str;
      default = "/var/lib/personal-stack/secrets/k3s/agent-token";
      description = "Runtime path of the worker join token copied onto k3s agent nodes.";
    };
  };

  config = lib.mkIf config.services.k3s.enable {
    assertions = lib.optionals isK3sAgent [
      {
        assertion = cfg.apiServerEndpoint != "";
        message = "k3s agent nodes require personalStack.k3sBootstrap.apiServerEndpoint";
      }
      {
        assertion = cfg.workerJoinTokenFile != "";
        message = "k3s agent nodes require personalStack.k3sBootstrap.workerJoinTokenFile";
      }
    ];

    networking.firewall.allowedTCPPorts =
      [ 10250 ]
      ++ lib.optionals isK3sServer [ 6443 ];
    networking.firewall.allowedUDPPorts = [ 8472 ];

    systemd.tmpfiles.rules = [
      "d ${tokenDirectory} 0700 root root - -"
    ];

    # tailscale0 must have its tailnet IP before k3s starts. During NixOS
    # activation tailscaled can be restarted immediately before k3s; the
    # interface exists, but k3s exits fatally if flannel checks it before the
    # global unicast address is assigned.
    systemd.services.k3s = {
      after = [ "tailscaled.service" ];
      requires = [ "tailscaled.service" ];
      preStart = lib.mkBefore ''
        attempt=0
        while [ "$attempt" -lt 60 ]; do
          if [ -n "$(${pkgs.iproute2}/bin/ip -o -4 addr show dev tailscale0 scope global 2>/dev/null)" ]; then
            exit 0
          fi
          attempt=$((attempt + 1))
          ${pkgs.coreutils}/bin/sleep 1
        done

        echo "tailscale0 did not receive a global IPv4 address within 60s" >&2
        exit 1
      '';
    };

    services.k3s = lib.mkMerge [
      {
        # Flannel has to tunnel over the tailnet because frankfurt (public
        # VPS) and the enschede LAN hosts cannot reach each other's primary
        # interface directly. Every node is already on tailscale0, so
        # pinning flannel to that iface gives the cluster a single routable
        # overlay network.
        extraFlags = [ "--flannel-iface=tailscale0" ];
      }
      (lib.mkIf isK3sAgent {
        serverAddr = lib.mkDefault cfg.apiServerEndpoint;
        extraFlags = lib.mkAfter [ "--token-file=${cfg.workerJoinTokenFile}" ];
      })
    ];
  };
}
