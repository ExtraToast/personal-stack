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
  # IMPORTANT: we do NOT use `sed -i` here. `sed -i` creates a temp
  # file via mkstemp() (mode 0600, root-owned since preStart runs as
  # root) and renames it over the original — that replaces the inode
  # and destroys the DynamicUser ownership + mode of the original
  # file. AdGuardHome then runs as the dynamic UID and fails with
  # "open ...AdGuardHome.yaml: permission denied", crashlooping
  # forever. Using `sed > tmp && cat tmp > original` keeps the
  # original inode (cat truncates and writes in place) so the file's
  # uid/gid/mode stay intact.
  #
  # The sed range is anchored on the `http:` block header and stops at
  # the next top-level YAML key, so no unrelated `address:` keys (DNS,
  # clients, etc.) are rewritten.
  systemd.services.adguardhome.preStart = lib.mkAfter ''
    config=/var/lib/AdGuardHome/AdGuardHome.yaml
    if [ -e "$config" ]; then
      tmp=$(${pkgs.coreutils}/bin/mktemp)
      ${pkgs.gnused}/bin/sed -E \
        '/^http:/,/^[[:alnum:]_-]+:/{s|^(\s+address:).*|\1 0.0.0.0:3000|}' \
        "$config" > "$tmp"
      ${pkgs.coreutils}/bin/cat "$tmp" > "$config"
      ${pkgs.coreutils}/bin/rm -f "$tmp"
    fi
  '';
}
