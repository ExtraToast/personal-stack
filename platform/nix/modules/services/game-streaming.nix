{ config, lib, pkgs, ... }:
let
  gameUser = "gamehost";
  gameHome = "/home/${gameUser}";
  gamesMount = "/srv/games";
  pcGamesMount = "${gamesMount}/pc";
  wolfState = "/etc/wolf";
  wolfSteamApp = ''
        [[profiles.apps]]
        title = "Steam"
        start_virtual_compositor = true
        app_state_folder = "steam"
        icon_png_path = "https://games-on-whales.github.io/wildlife/apps/steam/assets/icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "WolfSteam"
            image = "ghcr.io/games-on-whales/steam:edge"
            devices = []
            env = [
                "RUN_SWAY=true",
                "STEAM_STARTUP_FLAGS=-bigpicture",
                "GOW_REQUIRED_DEVICES=/dev/input/* /dev/dri/* /dev/nvidia*",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "${pcGamesMount}/steam:/home/retro/Games/SteamLibrary:rw",
                "${pcGamesMount}/downloads:/home/retro/Downloads:rw"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["NET_RAW", "MKNOD", "NET_ADMIN", "SYS_ADMIN", "SYS_NICE"],
                "SecurityOpt": ["seccomp=unconfined", "apparmor=unconfined"],
                "Ulimits": [{"Name":"nofile", "Hard":10240, "Soft":10240}],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """
  '';
  wolfHeroicApp = ''

        [[profiles.apps]]
        title = "Heroic"
        start_virtual_compositor = true
        app_state_folder = "heroic"
        icon_png_path = "https://games-on-whales.github.io/wildlife/apps/heroic-games-launcher/assets/icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "WolfHeroic"
            image = "ghcr.io/games-on-whales/heroic-games-launcher:edge"
            devices = []
            env = [
                "RUN_SWAY=true",
                "GOW_REQUIRED_DEVICES=/dev/input/* /dev/dri/* /dev/nvidia*",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "${pcGamesMount}/heroic:/home/retro/Games/Heroic:rw",
                "${pcGamesMount}/downloads:/home/retro/Downloads:rw"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["SYS_ADMIN", "SYS_NICE", "SYS_PTRACE", "NET_RAW", "MKNOD", "NET_ADMIN"],
                "SecurityOpt": ["seccomp=unconfined", "apparmor=unconfined"],
                "Ulimits": [{"Name":"nofile", "Hard":10240, "Soft":10240}],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """
  '';
  wolfLutrisApp = ''

        [[profiles.apps]]
        title = "Lutris"
        start_virtual_compositor = true
        app_state_folder = "lutris"
        icon_png_path = "https://games-on-whales.github.io/wildlife/apps/lutris/assets/icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "WolfLutris"
            image = "ghcr.io/games-on-whales/lutris:edge"
            devices = []
            env = [
                "RUN_SWAY=true",
                "WINEPREFIX=/home/retro/Games/Prefixes/default",
                "GOW_REQUIRED_DEVICES=/dev/input/* /dev/dri/* /dev/nvidia*",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "${pcGamesMount}/lutris:/home/retro/Games/Lutris:rw",
                "${pcGamesMount}/prefixes:/home/retro/Games/Prefixes:rw",
                "${pcGamesMount}/downloads:/home/retro/Downloads:rw"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["SYS_ADMIN", "SYS_NICE", "SYS_PTRACE", "NET_RAW", "MKNOD", "NET_ADMIN"],
                "SecurityOpt": ["seccomp=unconfined", "apparmor=unconfined"],
                "Ulimits": [{"Name":"nofile", "Hard":10240, "Soft":10240}],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """
  '';
  wolfStoreApps = wolfSteamApp + wolfHeroicApp + wolfLutrisApp;
  wolfBaseApps = ''
        [[profiles.apps]]
        title = "Wolf UI"
        start_virtual_compositor = true
        icon_png_path = "https://raw.githubusercontent.com/games-on-whales/wolf-ui/refs/heads/main/src/Icons/wolf_ui_icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "Wolf-UI"
            image = "ghcr.io/games-on-whales/wolf-ui:main"
            devices = []
            env = [
                "GOW_REQUIRED_DEVICES=/dev/input/event* /dev/dri/* /dev/nvidia*",
                "WOLF_SOCKET_PATH=/var/run/wolf/wolf.sock",
                "WOLF_UI_AUTOUPDATE=False",
                "LOGLEVEL=INFO",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "/var/run/wolf/wolf.sock:/var/run/wolf/wolf.sock"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["NET_RAW", "MKNOD", "NET_ADMIN", "SYS_ADMIN", "SYS_NICE"],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """

        [[profiles.apps]]
        title = "RetroArch"
        start_virtual_compositor = true
        app_state_folder = "retroarch"
        icon_png_path = "https://games-on-whales.github.io/wildlife/apps/retroarch/assets/icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "WolfRetroArch"
            image = "ghcr.io/games-on-whales/retroarch:edge"
            devices = []
            env = [
                "RUN_SWAY=1",
                "GOW_REQUIRED_DEVICES=/dev/input/* /dev/dri/* /dev/nvidia*",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "${gamesMount}:/ROMs:ro",
                "${gamesMount}:/games:ro"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["NET_RAW", "MKNOD", "NET_ADMIN"],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """

        [[profiles.apps]]
        title = "EmulationStation"
        start_virtual_compositor = true
        app_state_folder = "es-de"
        icon_png_path = "https://games-on-whales.github.io/wildlife/apps/es-de/assets/icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "WolfES-DE"
            image = "ghcr.io/games-on-whales/es-de:edge"
            devices = []
            env = [
                "RUN_SWAY=1",
                "MOZ_ENABLE_WAYLAND=1",
                "GOW_REQUIRED_DEVICES=/dev/input/* /dev/dri/* /dev/nvidia*",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "${gamesMount}:/ROMs:ro",
                "${gamesMount}:/games:ro"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["NET_RAW", "MKNOD", "NET_ADMIN"],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """

        [[profiles.apps]]
        title = "Desktop"
        start_virtual_compositor = true
        app_state_folder = "xfce"
        icon_png_path = "https://games-on-whales.github.io/wildlife/apps/xfce/assets/icon.png"

            [profiles.apps.runner]
            type = "docker"
            name = "WolfXFCE"
            image = "ghcr.io/games-on-whales/xfce:edge"
            devices = []
            env = [
                "GOW_REQUIRED_DEVICES=/dev/input/* /dev/dri/* /dev/nvidia*",
                "NVIDIA_VISIBLE_DEVICES=all",
                "NVIDIA_DRIVER_CAPABILITIES=all"
            ]
            mounts = [
                "${gamesMount}:/ROMs:ro",
                "${gamesMount}:/games:ro"
            ]
            ports = []
            base_create_json = """
            {
              "HostConfig": {
                "IpcMode": "host",
                "CapAdd": ["SYS_ADMIN", "SYS_NICE", "SYS_PTRACE", "NET_RAW", "MKNOD", "NET_ADMIN"],
                "SecurityOpt": ["seccomp=unconfined", "apparmor=unconfined"],
                "Privileged": false,
                "DeviceRequests": [
                  {
                    "Driver": "nvidia",
                    "Count": -1,
                    "Capabilities": [["gpu"]]
                  }
                ],
                "DeviceCgroupRules": ["c 13:* rmw", "c 244:* rmw"]
              }
            }
            """
  '';
  wolfConfigSeed = pkgs.writeText "wolf-config.toml" (
    ''
      hostname = "enschede-gtx-960m-1"
      support_hevc = false
      config_version = 2
      uuid = "96000000-0000-4000-8000-000000000960"

      paired_clients = []
      gstreamer = {}

      [[profiles]]
      id = "moonlight-profile-id"
      name = "Moonlight"

    ''
    + wolfBaseApps
    + wolfStoreApps
  );
  wolfSteamAppFile = pkgs.writeText "wolf-steam-app.toml" wolfSteamApp;
  wolfHeroicAppFile = pkgs.writeText "wolf-heroic-app.toml" wolfHeroicApp;
  wolfLutrisAppFile = pkgs.writeText "wolf-lutris-app.toml" wolfLutrisApp;
in
{
  users.users.${gameUser} = {
    isNormalUser = true;
    uid = 1001;
    home = gameHome;
    createHome = true;
    description = "Moonlight game streaming state owner";
    extraGroups = [
      "audio"
      "docker"
      "input"
      "render"
      "uinput"
      "video"
    ];
  };

  hardware.uinput.enable = true;

  boot.kernelModules = [
    "nvidia"
    "nvidia_uvm"
    "nvidia_drm"
    "uhid"
    "uinput"
  ];
  boot.kernelParams = [ "nvidia-drm.modeset=1" ];

  security.rtkit.enable = true;
  services.pipewire = {
    enable = true;
    alsa.enable = true;
    pulse.enable = true;
  };

  virtualisation.docker = {
    enable = true;
    autoPrune.enable = true;
  };
  virtualisation.oci-containers = {
    backend = "docker";
    containers.wolf = {
      image = "ghcr.io/games-on-whales/wolf:stable";
      autoStart = true;
      # Wolf creates per-session virtual displays, so Moonlight clients can
      # request 720p, 1080p, 1440p, 4K30, or best-effort 4K60 without X11 modes.
      environment = {
        HOST_APPS_STATE_FOLDER = wolfState;
        NVIDIA_DRIVER_CAPABILITIES = "all";
        NVIDIA_VISIBLE_DEVICES = "all";
        WOLF_CFG_FILE = "${wolfState}/cfg/config.toml";
        WOLF_DOCKER_SOCKET = "/var/run/docker.sock";
        WOLF_LOG_LEVEL = "INFO";
        WOLF_RENDER_NODE = "/dev/dri/renderD128";
        WOLF_SOCKET_PATH = "/var/run/wolf/wolf.sock";
        WOLF_STOP_CONTAINER_ON_EXIT = "TRUE";
      };
      volumes = [
        "${wolfState}:${wolfState}:rw"
        "/run/wolf:/var/run/wolf:rw"
        "/var/run/docker.sock:/var/run/docker.sock:rw"
        "/dev:/dev:rw"
        "/run/udev:/run/udev:rw"
        "${gamesMount}:${gamesMount}:ro"
      ];
      extraOptions = [
        "--network=host"
        "--gpus=all"
        "--device=/dev/dri"
        "--device=/dev/uinput"
        "--device=/dev/uhid"
        "--device-cgroup-rule=c 13:* rmw"
        "--device-cgroup-rule=c 244:* rmw"
      ];
    };
  };

  environment.systemPackages = with pkgs; [
    docker
    docker-compose
    exfatprogs
    mesa-demos
    moonlight-qt
    ntfs3g
    pciutils
    vulkan-tools
  ];

  systemd.tmpfiles.rules = [
    "d ${gamesMount} 0755 root root - -"
    "d ${pcGamesMount} 0775 ${gameUser} users - -"
    "d ${pcGamesMount}/downloads 0775 ${gameUser} users - -"
    "d ${pcGamesMount}/heroic 0775 ${gameUser} users - -"
    "d ${pcGamesMount}/lutris 0775 ${gameUser} users - -"
    "d ${pcGamesMount}/prefixes 0775 ${gameUser} users - -"
    "d ${pcGamesMount}/steam 0775 ${gameUser} users - -"
    "d /var/lib/personal-stack/wolfmanager 0750 root docker - -"
    "d /var/lib/personal-stack/wolfmanager/config 0750 root docker - -"
    "d /run/wolf 0750 root docker - -"
    "d ${wolfState} 0750 root docker - -"
    "d ${wolfState}/cfg 0750 root docker - -"
    "d ${wolfState}/profile_data 0750 ${gameUser} users - -"
    "d ${gameHome}/.config 0755 ${gameUser} users - -"
    "d ${gameHome}/.local 0755 ${gameUser} users - -"
    "d ${gameHome}/.local/share 0755 ${gameUser} users - -"
  ];

  systemd.services.wolf-config-seed = {
    description = "Seed Wolf Moonlight streaming configuration";
    wantedBy = [ "multi-user.target" ];
    before = [ "docker-wolf.service" ];
    serviceConfig = {
      Type = "oneshot";
      RemainAfterExit = true;
    };
    script = ''
      install -d -m 0750 -o root -g docker ${wolfState}/cfg
      if [ ! -e ${wolfState}/cfg/config.toml ]; then
        install -m 0640 -o root -g docker ${wolfConfigSeed} ${wolfState}/cfg/config.toml
      fi
    '';
  };

  systemd.services.wolf-config-reconcile = {
    description = "Append missing Wolf PC launcher applications";
    wantedBy = [ "multi-user.target" ];
    after = [ "wolf-config-seed.service" ];
    before = [ "docker-wolf.service" ];
    requires = [ "wolf-config-seed.service" ];
    serviceConfig = {
      Type = "oneshot";
      RemainAfterExit = true;
    };
    script = ''
      config=${wolfState}/cfg/config.toml
      backup=${wolfState}/cfg/config.toml.pre-store-launchers

      if [ ! -e "$backup" ]; then
        cp -a "$config" "$backup"
      fi

      append_app() {
        title="$1"
        app_file="$2"
        if ! grep -Fq "title = \"$title\"" "$config" && ! grep -Fq "title = '$title'" "$config"; then
          printf '\n' >> "$config"
          cat "$app_file" >> "$config"
        fi
      }

      append_app Steam ${wolfSteamAppFile}
      append_app Heroic ${wolfHeroicAppFile}
      append_app Lutris ${wolfLutrisAppFile}

      chown root:docker "$config"
      chmod 0640 "$config"
    '';
  };

  systemd.services.docker-wolf = {
    after = [
      "docker.service"
      "network-online.target"
      "wolf-config-reconcile.service"
      "wolf-config-seed.service"
    ];
    requires = [
      "docker.service"
      "wolf-config-reconcile.service"
      "wolf-config-seed.service"
    ];
  };

  fileSystems.${gamesMount} = {
    device = "/dev/disk/by-label/GameRoms";
    fsType = "auto";
    options = [
      "nofail"
      "noauto"
      "x-systemd.automount"
      "x-systemd.device-timeout=10s"
      "x-systemd.idle-timeout=10min"
    ];
  };

  networking.firewall = {
    allowedTCPPorts = [
      47984
      47989
      47990
      48010
    ];
    allowedUDPPortRanges = [
      {
        from = 47998;
        to = 48010;
      }
      {
        from = 8000;
        to = 8010;
      }
    ];
  };

  assertions = [
    {
      assertion = config.hardware.nvidia.modesetting.enable;
      message = "game-streaming.nix requires the NVIDIA driver profile with DRM modesetting enabled.";
    }
    {
      assertion = config.hardware.nvidia-container-toolkit.enable;
      message = "game-streaming.nix requires NVIDIA Container Toolkit for Wolf Docker GPU access.";
    }
  ];
}
