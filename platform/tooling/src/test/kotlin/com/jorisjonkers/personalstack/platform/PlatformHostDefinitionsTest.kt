package com.jorisjonkers.personalstack.platform

import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlatformHostDefinitionsTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val fleet =
        PlatformFleetLoader().load(
            repositoryRoot.resolve("platform/inventory/fleet.yaml"),
        )

    @Test
    fun `every inventory node has a nix host definition and disk layout`() {
        fleet.nodes.keys.forEach { nodeName ->
            assertThat(Files.exists(repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix")))
                .describedAs("host %s should define a default.nix", nodeName)
                .isTrue()
            assertThat(Files.exists(repositoryRoot.resolve("platform/nix/hosts/${nodeName}/disko.nix")))
                .describedAs("host %s should define a disko.nix", nodeName)
                .isTrue()
        }
    }

    @Test
    fun `host definitions import profiles implied by fleet roles and capabilities`() {
        fleet.nodes.forEach { (nodeName, node) ->
            val hostDefinition = repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix").toFile().readText()

            if ("k3s-control-plane" in node.targetRoles) {
                assertThat(hostDefinition).contains("../../profiles/control-plane.nix")
            }
            if ("k3s-worker" in node.targetRoles) {
                assertThat(hostDefinition).contains("../../profiles/worker.nix")
            }
            if ("utility-host" in node.targetRoles) {
                assertThat(hostDefinition).contains("../../profiles/utility.nix")
            }
            if ("nvidia" in node.capabilities) {
                assertThat(hostDefinition).contains("../../profiles/gpu-nvidia.nix")
            }
        }
    }

    @Test
    fun `raspberry pi hosts override efi boot with generic extlinux`() {
        listOf("enschede-pi-1", "enschede-pi-2", "enschede-pi-3").forEach { nodeName ->
            val hostDefinition = repositoryRoot.resolve("platform/nix/hosts/${nodeName}/default.nix").toFile().readText()

            assertThat(hostDefinition)
                .contains("imageBuild ? false")
                .contains("lib.optional (!imageBuild) ./disko.nix")
                .contains("systemd-boot.enable = lib.mkForce false")
                .contains("efi.canTouchEfiVariables = lib.mkForce false")
                .contains("generic-extlinux-compatible.enable = true")
        }
    }

    @Test
    fun `flake exports host specific raspberry pi sd images`() {
        val flake = repositoryRoot.resolve("platform/flake.nix").toFile().readText()
        val buildScript = repositoryRoot.resolve("platform/scripts/build/build-pi-image.sh").toFile().readText()

        listOf("enschede-pi-1", "enschede-pi-2", "enschede-pi-3").forEach { nodeName ->
            assertThat(flake).contains("${nodeName} = mkHost {")
            assertThat(flake).contains("extraSpecialArgs = { imageBuild = true; };")
            assertThat(flake).contains("piSdImages =")
            assertThat(buildScript).contains("#piSdImages.\${NODE_NAME}")
        }
    }

    @Test
    fun `flake exports every inventory node with the correct architecture`() {
        val flake = repositoryRoot.resolve("platform/flake.nix").toFile().readText()

        fleet.nodes.forEach { (nodeName, node) ->
            assertThat(flake).contains("${nodeName} = mkHost")

            val expectedSystem =
                when (node.arch) {
                    "amd64" -> "x86_64-linux"
                    "arm64" -> "aarch64-linux"
                    else -> error("Unsupported arch ${node.arch}")
                }
            assertThat(flake).contains("${nodeName} = mkHost {")
            assertThat(flake).contains("system = \"${expectedSystem}\";")
            assertThat(flake).contains("hostModule = ./nix/hosts/${nodeName}/default.nix;")
        }
    }

    @Test
    fun `flake exports deploy targets for every ssh reachable inventory node`() {
        val flake = repositoryRoot.resolve("platform/flake.nix").toFile().readText()

        fleet.nodes.forEach { (nodeName, node) ->
            node.ssh?.let {
                val expectedSystem =
                    when (node.arch) {
                        "amd64" -> "x86_64-linux"
                        "arm64" -> "aarch64-linux"
                        else -> error("Unsupported arch ${node.arch}")
                    }

                assertThat(flake).contains("deploy.nodes.${nodeName}")
                assertThat(flake).contains("hostname = \"${it.host}\"")
                assertThat(flake).contains("sshUser = \"${it.user}\"")
                assertThat(flake).contains("sshOpts = [ \"-p\" \"${it.port}\" ]")
                assertThat(flake)
                    .contains("deploy-rs.lib.${expectedSystem}.activate.nixos self.nixosConfigurations.${nodeName}")
            }
        }
    }

    @Test
    fun `gtx 960m host imports game streaming service`() {
        val hostDefinition =
            repositoryRoot.resolve("platform/nix/hosts/enschede-gtx-960m-1/default.nix").toFile().readText()
        val module = repositoryRoot.resolve("platform/nix/modules/services/game-streaming.nix").toFile().readText()
        val gpuProfile = repositoryRoot.resolve("platform/nix/profiles/gpu-nvidia.nix").toFile().readText()
        val gtxNode = fleet.nodes.getValue("enschede-gtx-960m-1")

        assertThat(gtxNode.capabilities).contains("game-streaming", "nvidia")
        assertThat(fleet.serviceIntent.hostNative.getValue("enschede-gtx-960m-1"))
            .contains("game-streaming")
        assertThat(hostDefinition)
            .contains("../../modules/services/game-streaming.nix")
            .contains("\"personal-stack/capability-game-streaming\" = \"true\"")

        assertThat(module)
            .contains("ghcr.io/games-on-whales/wolf:stable")
            .contains("ghcr.io/games-on-whales/retroarch:edge")
            .contains("ghcr.io/games-on-whales/es-de:edge")
            .contains("ghcr.io/games-on-whales/xfce:edge")
            .contains("ghcr.io/games-on-whales/steam:edge")
            .contains("ghcr.io/games-on-whales/heroic-games-launcher:edge")
            .contains("ghcr.io/games-on-whales/lutris:edge")
            .contains("support_hevc = false")
            .contains("Wolf UI")
            .contains("title = \"Steam\"")
            .contains("title = \"Heroic\"")
            .contains("title = \"Lutris\"")
            .contains("STEAM_STARTUP_FLAGS=-bigpicture")
            .contains("WINEPREFIX=/home/retro/Games/Prefixes/default")
            .contains("4K60")
            .contains("virtualisation.docker")
            .contains("virtualisation.oci-containers")
            .contains("WOLF_RENDER_NODE = \"/dev/dri/renderD128\"")
            .contains("WOLF_SOCKET_PATH = \"/var/run/wolf/wolf.sock\"")
            .contains("\"/run/wolf:/var/run/wolf:rw\"")
            .contains("d /var/lib/personal-stack/wolfmanager/config")
            .contains("\"DeviceRequests\"")
            .contains("--gpus=all")
            .contains("--network=host")
            .contains("--device=/dev/uinput")
            .contains("--device=/dev/uhid")
            .contains("hardware.uinput.enable = true")
            .contains("nvidia-drm.modeset=1")
            .contains("boot.kernelModules")
            .contains("services.pipewire")
            .contains("uid = 1001")
            .contains("device = \"/dev/disk/by-label/GameRoms\"")
            .contains("fileSystems.\${gamesMount}")
            .contains("wolf-config-seed")
            .contains("wolf-config-reconcile")
            .contains("config.toml.pre-store-launchers")
            .contains("append_app Steam")
            .contains("append_app Heroic")
            .contains("append_app Lutris")
            .contains("\${wolfState}/cfg/config.toml")
            .contains("\${gamesMount}:/ROMs:ro")
            .contains("\${pcGamesMount}/steam:/home/retro/Games/SteamLibrary:rw")
            .contains("\${pcGamesMount}/heroic:/home/retro/Games/Heroic:rw")
            .contains("\${pcGamesMount}/lutris:/home/retro/Games/Lutris:rw")
            .contains("\${pcGamesMount}/prefixes:/home/retro/Games/Prefixes:rw")
            .contains("\${pcGamesMount}/downloads:/home/retro/Downloads:rw")
            .contains("47984")
            .contains("47989")
            .contains("47990")
            .contains("48010")
            .contains("from = 8000")
            .contains("to = 8010")
            .contains("hardware.nvidia-container-toolkit.enable")

        assertThat(gpuProfile)
            .contains("lib.hasPrefix \"nvidia-\" name")
            .contains("lib.hasPrefix \"cuda\" name")
            .contains("lib.hasPrefix \"libcu\" name")
            .contains("lib.hasPrefix \"libn\" name")
            .contains("lib.hasPrefix \"libnv\" name")
            .contains("CUDA EULA")
            .contains("lib.hasPrefix \"libretro-\" name")
    }
}
