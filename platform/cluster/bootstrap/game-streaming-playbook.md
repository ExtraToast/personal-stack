# Game Streaming Playbook

This host-native service runs Games On Whales Wolf on `enschede-gtx-960m-1`.
Wolf exposes virtual displays to Moonlight clients, so the client can request
720p, 1080p, 1440p, 4K30, or 4K60 without a physical monitor or dummy HDMI plug.

## Storage

The external Seagate OneTouch Wii/GameCube drive is mounted at `/srv/games` by
filesystem UUID. The FAT32 filesystem currently has no label, and the code uses
the UUID to avoid writing a label to a Wii-compatible disk:

```sh
ls -la /dev/disk/by-uuid/1120-414D
systemctl status srv-games.automount
ls -la /srv/games
```

This mount is read-only on the GTX host. Do not rename, repair, relabel, or
copy into the Wii/GameCube paths from Linux unless that is an explicit recovery
operation. The drive must remain compatible with the existing Wii setup.

Protected external paths discovered on the drive:

- GameCube: `/srv/games/games/<title [GAMEID]>/game.iso`, `game.ciso`, and
  occasional `disc2.iso`. Current scan found 190 title directories and 202 disc
  files.
- Wii: `/srv/games/wbfs/<title [GAMEID]>/<GAMEID>.wbfs`, with at least one
  split `.wbf1` file. Current scan found 212 Wii title directories.
- Nintendont memory cards: `/srv/games/saves/GALP.raw`,
  `/srv/games/saves/GZLE.raw`, and `/srv/games/saves/ninmem.raw`.

Wolf app containers receive the external drive read-only as both `/ROMs` and
`/games`, so RetroArch/ES-DE can browse `/ROMs/games`, `/ROMs/wbfs`, and
`/ROMs/saves` without mutating them.

The GTX internal SSD has a separate writable workspace at `/srv/game-streaming`.
Use this for PC launchers, imported ROMs, Switch images, downloads, and anything
that does not need to stay on the Wii-compatible FAT32 disk.

PC launchers use writable library directories under `/srv/game-streaming/pc`:

- Steam: `/srv/game-streaming/pc/steam`
- Epic, GOG, and Amazon Prime Games through Heroic:
  `/srv/game-streaming/pc/heroic`
- EA App, Ubisoft Connect, Battle.net, itch.io, and generic Wine games through
  Lutris: `/srv/game-streaming/pc/lutris`
- Shared Wine prefixes and installers: `/srv/game-streaming/pc/prefixes` and
  `/srv/game-streaming/pc/downloads`

Local/imported emulator content can live under:

- `/srv/game-streaming/roms`
- `/srv/game-streaming/imports/t1000/Emulation`

RetroArch and ES-DE receive those SSD paths as `/ROMs-local` and `/games-local`
in addition to the protected external `/ROMs` and `/games` mounts.

To inspect the t1000 media drive for game files without copying:

```sh
platform/scripts/game-streaming/copy-t1000-emulation-to-gtx.sh
```

To copy `/srv/media/Emulation` from t1000 to the GTX SSD import area:

```sh
platform/scripts/game-streaming/copy-t1000-emulation-to-gtx.sh --execute
```

That script copies to `/srv/game-streaming/imports/t1000/Emulation` and refuses
destinations under `/srv/games`. The t1000 scan found one Wii `.wbfs` under
`/srv/media/Emulation/ROMS/wii` and four Switch `.xci` files under
`/srv/media/Emulation/switch/games`; Switch images are too large for FAT32 as
single files and should stay on the GTX SSD if copied.

## Wolf

Browser management is exposed separately from gameplay:

- WolfManager URL: `https://wolf.jorisjonkers.dev`
- First login follows WolfManager upstream defaults unless a persisted config
  already exists under `/var/lib/personal-stack/wolfmanager/config`.
- The URL is protected by the stack forward-auth flow. Grant the `WOLF`
  service permission in the admin UI to make the MyApps card visible.

Check Wolf and Docker after deploy:

```sh
systemctl status wolf-config-seed.service
systemctl status wolf-config-reconcile.service
systemctl status docker-wolf.service
docker logs --tail 200 wolf
```

Check WolfManager after Flux reconciles:

```sh
kubectl -n utility-system get pods -l app.kubernetes.io/name=wolfmanager -o wide
kubectl -n utility-system logs deploy/wolfmanager --tail=100
kubectl -n utility-system get svc wolfmanager
```

The first deploy seeds `/etc/wolf/cfg/config.toml`. Wolf owns that file after
seeding because Moonlight pairing state is written into it. The reconcile unit
adds missing Steam, Heroic, and Lutris app entries to existing configs without
overwriting pairings or local Wolf UI changes. It writes one backup at
`/etc/wolf/cfg/config.toml.pre-store-launchers`.

## Moonlight Access

`https://wolf.jorisjonkers.dev` is the browser management UI, not the
GameStream endpoint. Moonlight should connect directly to the GTX node:

- LAN: add `enschede-gtx-960m-1` if local DNS resolves it, otherwise add the
  node's LAN IP.
- Tailnet: add `100.89.41.92`, or the Tailscale MagicDNS name for
  `enschede-gtx-960m-1` if MagicDNS is enabled on the client.

Automatic discovery may not show the host outside the same L2 network because
Moonlight discovery relies on local broadcast/mDNS-style traffic. Manual add is
expected on Tailscale, routed Wi-Fi/VLANs, and remote clients. Pairing and
streaming then use Wolf's native ports opened by the NixOS module:

- TCP: `47984`, `47989`, `47990`, `48010`
- UDP: `47998-48010`, `8000-8010`

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
ls -la /dev/dri/renderD129 /dev/uinput /dev/uhid /dev/nvidia*
docker run --rm --device=nvidia.com/gpu=all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
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
