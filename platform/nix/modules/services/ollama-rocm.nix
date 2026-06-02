{ config, lib, pkgs, ... }:
{
  # Host-native Ollama with ROCm acceleration. RDNA3 (gfx1100) is a
  # supported ROCm target in current rocm-llvm, so models load on
  # GPU without HSA_OVERRIDE_GFX_VERSION hacks. If a future ROCm
  # release drops gfx1100 from the default targets we'd set
  # HSA_OVERRIDE_GFX_VERSION=11.0.0 to pin against the nearest LLVM
  # codegen path.
  #
  # nixpkgs-unstable removed services.ollama.acceleration; the
  # selector is now the package variant, so pin `pkgs.ollama-rocm`
  # to get the ROCm-built daemon binary.
  services.ollama = {
    enable = true;
    package = pkgs.ollama-rocm;
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
    # qwen3:32b — single resident chat model on the 24 GiB RX 7900
    # XTX. Q4_K_M lands around 19–20 GiB on disk, leaving ~4 GiB
    # for the KV cache at the curator's typical 4–6 k context. Native
    # structured-output mode (no Ollama grammar overlay needed —
    # Qwen2.5 required one). Throughput is ~8–12 tok/s on this card;
    # plenty for the hourly curator cadence + bursty digest calls.
    # Pre-pulling at activation downloads the ~19 GiB weights to disk
    # so a fresh deploy doesn't surface the download as first-request
    # latency; residency itself is governed by OLLAMA_KEEP_ALIVE below,
    # which now unloads the model when idle. The cluster-side resolver
    # (#406 / #407) keeps qwen3:8b on the in-cluster CPU Ollama as the
    # fallback whenever this node is offline.
    loadModels = [
      "qwen3:32b"
    ];
    environmentVariables = {
      # Surface ROCm visible devices explicitly; with a single
      # 7900 XTX the index is 0.
      HIP_VISIBLE_DEVICES = "0";
      # Unload the 32B shortly after the last call so the card idles
      # its fans and clocks down between work. The old 60 m value, with
      # the curator probing every 5 minutes, held the weights resident
      # in VRAM around the clock — the RX 7900 XTX ran hot and loud
      # 24/7 to serve a workload that is bursty by nature. 5 m keeps the
      # model warm across a single curator pass's sequential per-note
      # calls and across a short interactive digest session, then frees
      # the GPU. The curator now runs hourly (see cronjob.yaml), so each
      # pass pays one ~30 s cold reload — negligible against ~55 idle
      # minutes per hour, and well inside the 300 s request timeout.
      OLLAMA_KEEP_ALIVE = "5m";
      # Single parallel slot. The 32B model + KV cache sits near
      # the 24 GiB ceiling; two concurrent contexts would OOM.
      # Concurrent callers serialize at Ollama's request queue
      # rather than at the GPU. Was 2 under the 14B model.
      OLLAMA_NUM_PARALLEL = "1";
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
