# Game Streaming Playbook

This host-native service runs Games On Whales Wolf on `enschede-gtx-960m-1`.
Wolf exposes virtual displays to Moonlight clients, so the client can request
720p, 1080p, 1440p, 4K30, or 4K60 without a physical monitor or dummy HDMI plug.

## Storage

The external ROM drive is mounted at `/srv/games` by label:

```sh
ls -la /dev/disk/by-label/GameRoms
systemctl status srv-games.automount
ls -la /srv/games
```

Wolf app containers receive that drive read-only as both `/ROMs` and `/games`.
Keep ROMs on the drive in frontend-friendly platform directories where possible.
For Wii/GameCube, prefer `/srv/games/wii` and `/srv/games/gc`; if the current
drive uses `/srv/games/wii-gc`, RetroArch can still browse it directly via
`/games/wii-gc`, but ES-DE may need per-system path configuration.

PC launchers use writable library directories under `/srv/games/pc`:

- Steam: `/srv/games/pc/steam`
- Epic, GOG, and Amazon Prime Games through Heroic: `/srv/games/pc/heroic`
- EA App, Ubisoft Connect, Battle.net, itch.io, and generic Wine games through
  Lutris: `/srv/games/pc/lutris`
- Shared Wine prefixes and installers: `/srv/games/pc/prefixes` and
  `/srv/games/pc/downloads`

## Wolf

Check Wolf and Docker after deploy:

```sh
systemctl status wolf-config-seed.service
systemctl status wolf-config-reconcile.service
systemctl status docker-wolf.service
docker logs --tail 200 wolf
```

The first deploy seeds `/etc/wolf/cfg/config.toml`. Wolf owns that file after
seeding because Moonlight pairing state is written into it. The reconcile unit
adds missing Steam, Heroic, and Lutris app entries to existing configs without
overwriting pairings or local Wolf UI changes. It writes one backup at
`/etc/wolf/cfg/config.toml.pre-store-launchers`.

Use the Moonlight app list as follows:

- `Steam`: Steam Big Picture and native Proton library management.
- `Heroic`: Epic Games Store, GOG, and Amazon Prime Games.
- `Lutris`: EA App, Ubisoft Connect, Battle.net, itch.io, standalone Windows
  installers, and custom Wine games.
- `Desktop`: fallback for manual setup or launchers that need a browser login.

Anti-cheat-heavy multiplayer games may fail under Linux/Wine/container
streaming even when the launcher itself works.

## 4K60 Validation

The GTX 960M exposes Wolf's virtual display flow, but 4K60 is best-effort on
this Maxwell-era GPU. Validate in this order:

```sh
nvidia-smi
cat /sys/module/nvidia_drm/parameters/modeset
ls -la /dev/dri/renderD128 /dev/uinput /dev/uhid /dev/nvidia*
docker run --rm --gpus=all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

Expected:

- `nvidia_drm` modeset reports `Y`.
- Docker can see the NVIDIA GPU.
- Moonlight pairs with host `enschede-gtx-960m-1`.
- RetroArch starts at 1080p60 and 1440p60.
- 4K30 is stable.
- 4K60 starts from Moonlight and runs for 5-10 minutes without severe frame
  drops. If 4K60 drops frames, keep it as an experimental profile and use
  1440p60 or 4K30 as the stable target.

Wolf is seeded with HEVC disabled for this GPU. Enable it in
`/etc/wolf/cfg/config.toml` only after confirming the runtime GStreamer/NVENC
path supports it on this driver/GPU combination.
