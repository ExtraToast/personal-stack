# Changelog

## [0.2.0](https://github.com/ExtraToast/personal-stack/compare/v0.1.0...v0.2.0) (2026-06-08)


### Features

* **agent-token:** request workflows, issues, packages:read in minted tokens ([#620](https://github.com/ExtraToast/personal-stack/issues/620)) ([a8152d2](https://github.com/ExtraToast/personal-stack/commit/a8152d21c765b53c0d74f1c6daf0a752e570dc8e))
* **app-ui:** swap MyApps Status tile icon to gatus.svg ([#185](https://github.com/ExtraToast/personal-stack/issues/185)) ([6aa52d8](https://github.com/ExtraToast/personal-stack/commit/6aa52d8257d5f343dc4c01c4ee5e43f94de1e675))
* **gatus:** probe readiness group + internal services (alloy, loki, tempo, postgres, valkey, prometheus, flaresolverr) ([#186](https://github.com/ExtraToast/personal-stack/issues/186)) ([07521ed](https://github.com/ExtraToast/personal-stack/commit/07521edca684f57a42ff418d8e3a7c4f0efe6853))
* **grafana:** wire OIDC login via auth-api ([#182](https://github.com/ExtraToast/personal-stack/issues/182)) ([56bf97b](https://github.com/ExtraToast/personal-stack/commit/56bf97b8a1cda7dce8b5a8922fc491ad97a2bdf3))
* **ingress:** per-service WAN-direct origin override, route jellyfin direct ([#191](https://github.com/ExtraToast/personal-stack/issues/191)) ([3a51011](https://github.com/ExtraToast/personal-stack/commit/3a51011838982501e30349a3d0ba1680cf054dac))
* **observability:** fleet-driven Gatus status page at status.jorisjonkers.dev ([#179](https://github.com/ExtraToast/personal-stack/issues/179)) ([7a6b002](https://github.com/ExtraToast/personal-stack/commit/7a6b0028cc275b65f9c24f5f09583a70668e3afb))
* **release:** release-please tagging, version-tagged images, Renovate ([#623](https://github.com/ExtraToast/personal-stack/issues/623)) ([d3da769](https://github.com/ExtraToast/personal-stack/commit/d3da769d0e6c288bb187bdb3c73aa8d3af2fd770))
* **t1000:** add ntfs3g userspace tools to system PATH ([#198](https://github.com/ExtraToast/personal-stack/issues/198)) ([520d7cf](https://github.com/ExtraToast/personal-stack/commit/520d7cffc4915d165786e33e149fc5d06746da0d))
* **t1000:** declare second M.2 as btrfs /srv/backup in disko ([#196](https://github.com/ExtraToast/personal-stack/issues/196)) ([e78c132](https://github.com/ExtraToast/personal-stack/commit/e78c132ecb1d1f2987f17c3275adf20c88af460b))


### Bug Fixes

* **alloy:** remove broken livenessProbe override ([#180](https://github.com/ExtraToast/personal-stack/issues/180)) ([d9b56db](https://github.com/ExtraToast/personal-stack/commit/d9b56dbc9c9026045354b29c8fecdfa35123111a))
* **app-ui:** repair account page + add My Apps nav + polish admin ([#170](https://github.com/ExtraToast/personal-stack/issues/170)) ([da623f2](https://github.com/ExtraToast/personal-stack/commit/da623f2aa89ac28b97c82403582c138ff14f053e))
* **auth-api:** Lettuce 500ms timeout + HikariCP keepalive ([#172](https://github.com/ExtraToast/personal-stack/issues/172)) ([edaa6e0](https://github.com/ExtraToast/personal-stack/commit/edaa6e04fa655e13ee5a35baa1ec099d46140a4e))
* **dns:** cap nameservers at 3 to stay under glibc MAXNS ([#184](https://github.com/ExtraToast/personal-stack/issues/184)) ([9518731](https://github.com/ExtraToast/personal-stack/commit/9518731a199ae16d45b80a1b7eff548c03810875))
* **dns:** Cloudflare upstream fleet-wide + ndots:2 on Jellyfin ([#178](https://github.com/ExtraToast/personal-stack/issues/178)) ([df15c29](https://github.com/ExtraToast/personal-stack/commit/df15c29c9e6d7455bfdea32a0de35d04810bc5a0))
* **dns:** write /etc/resolv.conf statically, disable openresolv merge ([#188](https://github.com/ExtraToast/personal-stack/issues/188)) ([59fd2e8](https://github.com/ExtraToast/personal-stack/commit/59fd2e84b8d4270c5efc016674cc09fda830a764))
* **grafana:** disable init-chown-data so rollouts succeed ([#181](https://github.com/ExtraToast/personal-stack/issues/181)) ([3913b53](https://github.com/ExtraToast/personal-stack/commit/3913b531c20bb3023d75c9cb1f63425c73349214))
* **jellyfin:** direct external DNS to unblock TMDb image fetches ([#175](https://github.com/ExtraToast/personal-stack/issues/175)) ([48041a2](https://github.com/ExtraToast/personal-stack/commit/48041a241862ce20bae895745013f00d3597f274))
* **lan-ingress:** move MetalLB VIP to 192.168.0.99 (on-LAN subnet) ([#189](https://github.com/ExtraToast/personal-stack/issues/189)) ([2584c7a](https://github.com/ExtraToast/personal-stack/commit/2584c7a280376e749fed08e927eee5bee76f0090))
* **media:** pin gluetun to v3.40.0 to restore cluster DNS ([#168](https://github.com/ExtraToast/personal-stack/issues/168)) ([d42d1d2](https://github.com/ExtraToast/personal-stack/commit/d42d1d24967f660e7c81ba8bdc205763b5129a10))
* **media:** tune qbittorrent/prowlarr probe delays to survive startup CPU spike ([#169](https://github.com/ExtraToast/personal-stack/issues/169)) ([98d2d68](https://github.com/ExtraToast/personal-stack/commit/98d2d68d78b49de2a0b3b56393803171bc9dc927))
* **metallb:** opt public traefik out of enschede-lan pool ([#190](https://github.com/ExtraToast/personal-stack/issues/190)) ([33f2d44](https://github.com/ExtraToast/personal-stack/commit/33f2d442aa423ddb9764618f96451179a162ea2d))
* **n8n:** deliver postgres dynamic creds via VSO so rotation restarts the pod ([#194](https://github.com/ExtraToast/personal-stack/issues/194)) ([eb7e7cb](https://github.com/ExtraToast/personal-stack/commit/eb7e7cb5235af160e4dca9788450e946d9ed10d8))
* **probes:** add startupProbe to RabbitMQ + raise Helm release timeouts ([#173](https://github.com/ExtraToast/personal-stack/issues/173)) ([bb5cb0e](https://github.com/ExtraToast/personal-stack/commit/bb5cb0e3e1a748bacebd469a4036891be11da6b3))
* **probes:** health check overhaul — correct endpoints + raise timeouts ([#171](https://github.com/ExtraToast/personal-stack/issues/171)) ([586e0d7](https://github.com/ExtraToast/personal-stack/commit/586e0d701d63653f166b5b4601f43e578707cae2))
* **rollout+n8n:** kill-then-create for Java APIs, relax n8n liveness ([#174](https://github.com/ExtraToast/personal-stack/issues/174)) ([d69a636](https://github.com/ExtraToast/personal-stack/commit/d69a636d539b329734e0bb6a6f5544ea2c41390c))
* **t1000:** nofail + device-timeout on /srv/backup mounts ([#197](https://github.com/ExtraToast/personal-stack/issues/197)) ([c2cef8d](https://github.com/ExtraToast/personal-stack/commit/c2cef8da5c85b04c4be4ea68e843ff813d74cdfe))
* **t1000:** pin disko disk.main to by-id path ([#199](https://github.com/ExtraToast/personal-stack/issues/199)) ([0b9f632](https://github.com/ExtraToast/personal-stack/commit/0b9f632e39ce1832d79ced4c0b9ba3ff3db6b936))
* **vault:** stop baking short-lived JWT into kube-auth, fail closed on inject ([#200](https://github.com/ExtraToast/personal-stack/issues/200)) ([8cea0c0](https://github.com/ExtraToast/personal-stack/commit/8cea0c0b7708df5fdfdc90deef1d8a37e8de2040))


### Performance Improvements

* **auth-api/assistant-api:** kill N+1 on permissions + add hot-path indexes ([#163](https://github.com/ExtraToast/personal-stack/issues/163)) ([7072a6b](https://github.com/ExtraToast/personal-stack/commit/7072a6b6eba330cb1160c9c1a814d755dbaf8a74))
* **auth-api:** Valkey-backed @Cacheable for user lookups with explicit eviction ([#164](https://github.com/ExtraToast/personal-stack/issues/164)) ([3902fd8](https://github.com/ExtraToast/personal-stack/commit/3902fd896cbb66e6a66aac9040e398b267be26fb))
* **platform:** cpu=limit + ZGC + OTel sampling for Java APIs ([#160](https://github.com/ExtraToast/personal-stack/issues/160)) ([89783d3](https://github.com/ExtraToast/personal-stack/commit/89783d3d97f0ad28ebc50bfdd4d5ce304eed0b0e))
* **postgres:** tuned postgresql.conf + pg_stat_statements + slow-query log ([#161](https://github.com/ExtraToast/personal-stack/issues/161)) ([f5fc154](https://github.com/ExtraToast/personal-stack/commit/f5fc1546206e346a511a2d9540e2687e7f2d28b4))
* **ui:** nginx cache headers for index.html + consistent image caching ([#165](https://github.com/ExtraToast/personal-stack/issues/165)) ([5715ae2](https://github.com/ExtraToast/personal-stack/commit/5715ae2ff23c87eb843269c1dd296a084d7d3b6d))


### Reverts

* **jellyfin:** drop dnsPolicy:None override — breaks cluster DNS ([#176](https://github.com/ExtraToast/personal-stack/issues/176)) ([7efc8b8](https://github.com/ExtraToast/personal-stack/commit/7efc8b80e7f93e88b2b30aa8bfc7a57eecb69a03))
* t1000 /srv/media — drop redundant device= mount options ([#233](https://github.com/ExtraToast/personal-stack/issues/233)) ([30d1a77](https://github.com/ExtraToast/personal-stack/commit/30d1a77e52972912c3e4d499b0f61675569ce1f0))
