{ config, lib, pkgs, ... }:
let
  cfg = config.personalStack.dualBootGpuReset;

  # The reset hook needs the GPU's PCI BDF baked in at build time. When it is
  # left null the generated script keeps the literal placeholder and self-skips
  # at runtime, so the flake still evaluates and a rebuild stays harmless until
  # an operator fills in the verified BDF. See the handoff runbook:
  #   docs/handoff/rx7900xtx-dualboot-fix/REMEDIATION-RUNBOOK.md
  gpuBdfStr = if cfg.gpuBdf == null then "REPLACE_WITH_GPU_BDF" else cfg.gpuBdf;

  # systemd-shutdown invokes hooks in /etc/systemd/system-shutdown with a single
  # argument: halt | poweroff | reboot | kexec. This hook is reboot-only — a real
  # poweroff already drops the GPU power rail (the strongest reset), and kexec is
  # not the firmware->Windows path. pkgs.writeShellScript supplies the shebang and
  # executable bit; NixOS links it in via the supported `systemd.shutdown` option
  # (do NOT use a nested environment.etc."systemd/system-shutdown/..." entry — it
  # collides with the directory NixOS already generates and breaks nixos-rebuild).
  resetHook = pkgs.writeShellScript "90-rx7900xtx-amdgpu-reset" ''
    set -u

    export PATH=${lib.makeBinPath [ pkgs.bash pkgs.coreutils ]}:''${PATH:-}

    reset_timeout="3s"
    action="''${1:-}"

    case "$action" in
      reboot) ;;
      *) exit 0 ;;
    esac

    gpu_bdf="${gpuBdfStr}"

    log() {
      printf '<6>rx7900xtx-amdgpu-reset: %s\n' "$*" > /dev/kmsg 2>/dev/null || true
    }

    run_timeout() {
      # Best-effort, NOT a hard non-blocking guarantee: `timeout` bounds normal
      # userspace commands, but it cannot preempt a child stuck in an
      # uninterruptible (D-state) kernel write to sysfs/debugfs during
      # gpu_recover/unbind/reset. On a wedged amdgpu path shutdown can still be
      # delayed until systemd-shutdown's own timeout.
      timeout --kill-after=1s "$reset_timeout" "$@" >/dev/null 2>&1
      rc=$?
      if [ "$rc" -ne 0 ]; then
        log "command failed or timed out rc=$rc: $*"
      fi
      return 0
    }

    if [ -z "$gpu_bdf" ] || [ "$gpu_bdf" = "REPLACE_WITH_GPU_BDF" ]; then
      log "GPU BDF not configured (personalStack.dualBootGpuReset.gpuBdf); skipping reset"
      exit 0
    fi

    dev="/sys/bus/pci/devices/$gpu_bdf"
    if [ ! -d "$dev" ]; then
      log "configured GPU BDF $gpu_bdf is not present; skipping reset"
      exit 0
    fi

    vendor="$(cat "$dev/vendor" 2>/dev/null || true)"
    class="$(cat "$dev/class" 2>/dev/null || true)"

    if [ "$vendor" != "0x1002" ]; then
      log "configured device $gpu_bdf vendor $vendor is not AMD; skipping reset"
      exit 0
    fi

    case "$class" in
      0x03*) ;;
      *)
        log "configured device $gpu_bdf class $class is not display-class; skipping reset"
        exit 0
        ;;
    esac

    log "starting reboot reset sequence for $gpu_bdf"

    # Step 1: while amdgpu is still bound, ask amdgpu's own recovery/reset path
    # to run if debugfs is mounted and exposes the node. Reading the node is
    # documented to trigger recovery on supported kernels. If absent, skip.
    for card in /sys/class/drm/card[0-9] /sys/class/drm/card[0-9][0-9]; do
      [ -e "$card/device" ] || continue
      target="$(readlink -f "$card/device" 2>/dev/null || true)"
      card_bdf="''${target##*/}"
      [ "$card_bdf" = "$gpu_bdf" ] || continue

      card_name="''${card##*/}"
      card_idx="''${card_name#card}"
      recover="/sys/kernel/debug/dri/$card_idx/amdgpu_gpu_recover"

      if [ -r "$recover" ]; then
        log "triggering amdgpu recovery via $recover"
        run_timeout bash -c 'cat "$1" >/dev/null' _ "$recover"
        sleep 1
      else
        log "amdgpu recovery node unavailable for $card_name"
      fi
    done

    # Step 2: unbind AMD functions in the same PCI slot, normally the display
    # function plus HDMI/DP audio. This avoids carrying Linux-bound function
    # state into the firmware/Windows handoff. Do not rescan during shutdown.
    slot="''${gpu_bdf%.*}"
    for fn in /sys/bus/pci/devices/"$slot".*; do
      [ -e "$fn" ] || continue
      bdf="''${fn##*/}"
      fn_vendor="$(cat "$fn/vendor" 2>/dev/null || true)"
      [ "$fn_vendor" = "0x1002" ] || continue

      if [ -L "$fn/driver" ]; then
        driver_target="$(readlink -f "$fn/driver" 2>/dev/null || true)"
        driver="''${driver_target##*/}"
        log "unbinding $bdf from $driver"
        run_timeout bash -c 'printf "%s" "$1" > "$2/driver/unbind"' _ "$bdf" "$fn"
      fi
    done

    sync
    sleep 1

    # Step 3: request the kernel PCI reset attribute for the display function if
    # the platform exposes it. This may not be a full ASIC power loss on every
    # board; that is why the reboot= cold path and fallbacks exist.
    if [ -w "$dev/reset" ]; then
      log "issuing PCI reset for $gpu_bdf"
      run_timeout bash -c 'printf "1" > "$1/reset"' _ "$dev"
      sleep 1
    else
      log "no writable PCI reset node for $gpu_bdf"
    fi

    log "reset sequence complete; continuing reboot"
    exit 0
  '';
in
{
  options.personalStack.dualBootGpuReset = {
    enable = lib.mkEnableOption ''
      a clean GPU handoff on the Linux->Windows reboot path for dual-boot AMD
      desktops. Investigation (docs/handoff/rx7900xtx-dualboot-fix) found that
      amdgpu leaves Navi31 (RX 7900 XTX) in a dirty, power-persistent on-die
      state across a WARM reboot; combined with Windows Fast Startup restoring a
      cached driver image against the mutated GPU, this corrupts the Windows AMD
      driver (DX12 dies) until a full DDU reinstall. This module forces a cold
      reboot vector and runs a guarded ASIC/PCI reset on the reboot path so the
      card is handed to firmware in a cleaner state. It is the Linux half of the
      fix; the Windows half (powercfg /h off) must also be applied
    '';

    gpuBdf = lib.mkOption {
      type = lib.types.nullOr (lib.types.strMatching "[0-9a-fA-F]{4}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}\\.[0-9a-fA-F]");
      default = null;
      example = "0000:03:00.0";
      description = ''
        Full PCI BDF (with domain) of the RX 7900 XTX VGA/Display/3D function,
        from `lspci -Dnn`. Use the display function (usually .0), NOT the HDMI/DP
        audio sibling. When null the reboot reset hook self-skips at runtime
        (only the cold reboot vector applies), so the flake still evaluates.
        Discover and set it per docs/handoff/rx7900xtx-dualboot-fix/NIXOS-BDF-DISCOVERY.md.
      '';
    };

    extraKernelParams = lib.mkOption {
      type = lib.types.listOf lib.types.str;
      default = [ ];
      example = [ "amdgpu.reset_method=2" ];
      description = ''
        One-at-a-time escalation fallbacks to append only after the minimal
        profile fails a controlled validation cycle. Candidates (enable exactly
        one per cycle, re-validate): "amdgpu.reset_method=2" (Navi mode1/PSP),
        "amdgpu.reset_method=4" (BACO/BAMACO), "amdgpu.gpu_recovery=1",
        "amdgpu.aspm=0", "amdgpu.runpm=0", "amdgpu.rebar=0", or an alternate
        reboot vector ("reboot=pci,cold" / "reboot=efi,cold"). See the runbook's
        bisection section.
      '';
    };
  };

  config = lib.mkIf cfg.enable {
    warnings = lib.optional (cfg.gpuBdf == null) ''
      personalStack.dualBootGpuReset is enabled but gpuBdf is null: the reboot
      reset hook will self-skip and only the cold reboot vector applies. Set
      personalStack.dualBootGpuReset.gpuBdf to the verified RX 7900 XTX display
      function BDF to activate the ASIC/PCI reset.
    '';

    # Force the cold reboot vector so the warm Linux->Windows reboot path drops
    # more state (kernel `reboot=` syntax: <bios|acpi|kbd|triple|efi|pci>,cold).
    boot.kernelParams = [ "reboot=acpi,cold" ] ++ cfg.extraKernelParams;

    systemd.shutdown."90-rx7900xtx-amdgpu-reset" = resetHook;
  };
}
