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
      supportedNixosAnywhereSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      mkHost =
        {
          system,
          hostModule,
          extraModules ? [ ],
          extraSpecialArgs ? { },
        }:
        lib.nixosSystem {
          inherit system;
          specialArgs = { inherit inputs; } // extraSpecialArgs;
          modules =
            [
              disko.nixosModules.disko
              hostModule
            ]
            ++ extraModules;
        };
      piSdImageConfigurations = {
        enschede-pi-1 = mkHost {
          system = "aarch64-linux";
          hostModule = ./nix/hosts/enschede-pi-1/default.nix;
          extraModules = [ ./nix/modules/image/raspberry-pi-sd-image.nix ];
          extraSpecialArgs = { imageBuild = true; };
        };
        enschede-pi-2 = mkHost {
          system = "aarch64-linux";
          hostModule = ./nix/hosts/enschede-pi-2/default.nix;
          extraModules = [ ./nix/modules/image/raspberry-pi-sd-image.nix ];
          extraSpecialArgs = { imageBuild = true; };
        };
        enschede-pi-3 = mkHost {
          system = "aarch64-linux";
          hostModule = ./nix/hosts/enschede-pi-3/default.nix;
          extraModules = [ ./nix/modules/image/raspberry-pi-sd-image.nix ];
          extraSpecialArgs = { imageBuild = true; };
        };
      };
    in
    {
      nixosConfigurations = {
        frankfurt-contabo-1 = mkHost {
          system = "x86_64-linux";
          hostModule = ./nix/hosts/frankfurt-contabo-1/default.nix;
        };
        enschede-gtx-960m-1 = mkHost {
          system = "x86_64-linux";
          hostModule = ./nix/hosts/enschede-gtx-960m-1/default.nix;
        };
        enschede-t1000-1 = mkHost {
          system = "x86_64-linux";
          hostModule = ./nix/hosts/enschede-t1000-1/default.nix;
        };
        # Pi host modules reference `imageBuild` in their imports list, which
        # forces the flag through specialArgs — otherwise NixOS resolves module
        # args lazily through `_module.args` and `nix flake check` loops on
        # infinite recursion. SD-image builds override to `true` below.
        enschede-pi-1 = mkHost {
          system = "aarch64-linux";
          hostModule = ./nix/hosts/enschede-pi-1/default.nix;
          extraSpecialArgs = { imageBuild = false; };
        };
        enschede-pi-2 = mkHost {
          system = "aarch64-linux";
          hostModule = ./nix/hosts/enschede-pi-2/default.nix;
          extraSpecialArgs = { imageBuild = false; };
        };
        enschede-pi-3 = mkHost {
          system = "aarch64-linux";
          hostModule = ./nix/hosts/enschede-pi-3/default.nix;
          extraSpecialArgs = { imageBuild = false; };
        };
      };

      piSdImages = lib.mapAttrs (_: configuration: configuration.config.system.build.sdImage) piSdImageConfigurations;

      deploy.nodes.frankfurt-contabo-1 = {
        hostname = "167.86.79.203";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.frankfurt-contabo-1;
        };
      };

      deploy.nodes.enschede-gtx-960m-1 = {
        hostname = "enschede-gtx-960m-1";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.enschede-gtx-960m-1;
        };
      };

      deploy.nodes.enschede-t1000-1 = {
        hostname = "100.103.175.110";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.x86_64-linux.activate.nixos self.nixosConfigurations.enschede-t1000-1;
        };
      };

      deploy.nodes.enschede-pi-1 = {
        hostname = "100.65.192.22";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.aarch64-linux.activate.nixos self.nixosConfigurations.enschede-pi-1;
        };
      };

      deploy.nodes.enschede-pi-2 = {
        hostname = "enschede-pi-2";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.aarch64-linux.activate.nixos self.nixosConfigurations.enschede-pi-2;
        };
      };

      deploy.nodes.enschede-pi-3 = {
        hostname = "enschede-pi-3";
        profiles.system = {
          sshUser = "deploy";
          user = "root";
          sshOpts = [ "-p" "2222" ];
          path = deploy-rs.lib.aarch64-linux.activate.nixos self.nixosConfigurations.enschede-pi-3;
        };
      };

      packages = lib.genAttrs supportedNixosAnywhereSystems (
        system:
        {
          nixos-anywhere = nixos-anywhere.packages.${system}.default;
        }
      );

      apps = lib.genAttrs supportedNixosAnywhereSystems (
        system:
        {
          nixos-anywhere = {
            type = "app";
            program = "${self.packages.${system}.nixos-anywhere}/bin/nixos-anywhere";
          };
        }
      );
    };
}
