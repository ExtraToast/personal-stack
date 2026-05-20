{ config, lib, pkgs, ... }:
{
  # Host-native Ollama with ROCm acceleration. RDNA3 (gfx1100) is a
  # supported ROCm target in current rocm-llvm, so models load on
  # GPU without HSA_OVERRIDE_GFX_VERSION hacks. If a future ROCm
  # release drops gfx1100 from the default targets we'd set
  # HSA_OVERRIDE_GFX_VERSION=11.0.0 to pin against the nearest LLVM
  # codegen path.
  services.ollama = {
    enable = true;
    acceleration = "rocm";
    # Listen on every interface so other pods on the tailnet can
    # reach the API directly without a k8s Service. Workloads in
    # the cluster (assistant-api, knowledge-curator) use this
    # endpoint via http://<host-tailnet-ip>:11434.
    host = "0.0.0.0";
    port = 11434;
    # Models live on the big drive; keep them outside /var so the
    # root partition isn't responsible for tens of GiB of weights.
    home = "/var/lib/ollama";
    models = "/var/lib/ollama/models";
    # Picked from the model the curator already uses (qwen2.5:14b-
    # instruct). Pulling at activation guarantees the model is
    # available before the assistant-api starts hitting it. Extra
    # entries here become `ollama pull` calls on boot.
    loadModels = [
      "qwen2.5:14b-instruct"
    ];
    environmentVariables = {
      # Surface ROCm visible devices explicitly; with a single
      # 7900 XTX the index is 0.
      HIP_VISIBLE_DEVICES = "0";
      # Keeps the daemon from evicting weights between sequential
      # requests on a single-GPU host.
      OLLAMA_KEEP_ALIVE = "30m";
      # Cap parallel slots so a runaway agent can't OOM the 24 GiB
      # VRAM during 14B inference.
      OLLAMA_NUM_PARALLEL = "2";
    };
  };

  # Ollama needs /dev/kfd + /dev/dri/renderD128 (provided by the AMD
  # role) and the `render` group membership. systemd's DynamicUser
  # path in services.ollama doesn't pick up `render` automatically,
  # so add it explicitly.
  systemd.services.ollama.serviceConfig.SupplementaryGroups = [
    "render"
    "video"
  ];

  networking.firewall.allowedTCPPorts = [ 11434 ];

  assertions = [
    {
      assertion = config.hardware.graphics.enable;
      message = "ollama-rocm.nix requires the AMD GPU profile (Mesa + ROCm userspace).";
    }
  ];
}
