# Deploy SSH Keys

Create one local public key file per node in this directory.

Naming rule:

- `<node-name>.pub`

Examples:

- `platform/nix/authorized-keys/frankfurt-contabo-1.pub`
- `platform/nix/authorized-keys/enschede-gtx-960m-1.pub`
- `platform/nix/authorized-keys/enschede-t1000-1.pub`

Each file must contain exactly one SSH public key line.

The NixOS base module reads the file that matches `networking.hostName` and
assigns only that key to `users.users.deploy.openssh.authorizedKeys.keys`.

These `.pub` files are gitignored on purpose.
