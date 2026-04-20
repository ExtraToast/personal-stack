{ lib, pkgs, ... }:
{
  services.adguardhome = {
    enable = true;
    mutableSettings = false;
    host = "0.0.0.0";
    port = 3000;
  };

  # AdGuard's first-boot wizard writes `http.address` into
  # `/var/lib/AdGuardHome/AdGuardHome.yaml`, and once that file exists
  # the nixpkgs module's --host/--port flags above become no-ops — the
  # UI binds to whatever the wizard picked (on this fleet that ended up
  # being the tailnet IP on port 80). That breaks the adguard-gateway
  # k8s Service + Endpoints, which expect the UI on :3000, so
  # adguard.jorisjonkers.dev stops resolving through Traefik.
  #
  # A full declarative `settings` block would fix the bind but would
  # also clobber UI-managed state on every activation (filter lists,
  # user bcrypt hashes, custom rewrite rules). Instead, patch just the
  # `http.address` key in the existing config before each service start
  # so the UI always listens on all interfaces at :3000, matching the
  # Service port, while leaving the rest of AdGuardHome.yaml untouched.
  #
  # The sed range is anchored on the `http:` block header and stops at
  # the next top-level YAML key, so no unrelated `address:` keys (DNS,
  # clients, etc.) are rewritten.
  systemd.services.adguardhome.preStart = lib.mkAfter ''
    if [ -e /var/lib/AdGuardHome/AdGuardHome.yaml ]; then
      ${pkgs.gnused}/bin/sed -i -E \
        '/^http:/,/^[[:alnum:]_-]+:/{s|^(\s+address:).*|\1 0.0.0.0:3000|}' \
        /var/lib/AdGuardHome/AdGuardHome.yaml
    fi
  '';
}
