{
  description = "Personal Stack NixOS and k3s platform";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    deploy-rs.url = "github:serokell/deploy-rs";
    disko.url = "github:nix-community/disko";
    nixos-anywhere.url = "github:nix-community/nixos-anywhere";
  };

  outputs =
    inputs@{ self, nixpkgs, deploy-rs, disko, nixos-anywhere, ... }:
    let
      lib = nixpkgs.lib;
      mkHost =
        system: hostModule:
        lib.nixosSystem {
          inherit system;
          specialArgs = { inherit inputs; };
          modules = [
            disko.nixosModules.disko
            hostModule
          ];
        };
    in
    {
      nixosConfigurations = {
        frankfurt-contabo-1 = mkHost "x86_64-linux" ./nix/hosts/frankfurt-contabo-1/default.nix;
        enschede-home-gtx960m-1 = mkHost "x86_64-linux" ./nix/hosts/enschede-home-gtx960m-1/default.nix;
        enschede-home-t1000-1 = mkHost "x86_64-linux" ./nix/hosts/enschede-home-t1000-1/default.nix;
        enschede-pi-1 = mkHost "aarch64-linux" ./nix/hosts/enschede-pi-1/default.nix;
        enschede-pi-2 = mkHost "aarch64-linux" ./nix/hosts/enschede-pi-2/default.nix;
        enschede-pi-3 = mkHost "aarch64-linux" ./nix/hosts/enschede-pi-3/default.nix;
      };

      deploy.nodes.frankfurt-contabo-1 = {
        hostname = "167.86.79.203";
        profiles.system = {
          sshUser = "root";
          user = "root";
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.frankfurt-contabo-1;
        };
      };

      deploy.nodes.enschede-home-gtx960m-1 = {
        hostname = "enschede-home-gtx960m-1";
        profiles.system = {
          sshUser = "root";
          user = "root";
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.enschede-home-gtx960m-1;
        };
      };

      packages.x86_64-linux.nixos-anywhere = nixos-anywhere.packages.x86_64-linux.default;
    };
}
