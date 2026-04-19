# Deploy SSH Keys

Put the fleet deploy public key(s) in `deploy.pub`.

Path: `platform/nix/authorized-keys/deploy.pub`

- Any non-empty, non-comment line is treated as an authorized key for the `deploy` user on every host.
- A single key is the default; multiple lines are supported for key rotation.
- The NixOS base module fails the build if this file is missing or empty.

Example:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/ps-fleet -C ps-fleet
cp ~/.ssh/ps-fleet.pub platform/nix/authorized-keys/deploy.pub
```

After editing, rebuild and redeploy each host (or rebuild and reflash the SD image for Pi nodes) for the new key to take effect.

`deploy.pub` is gitignored on purpose.
