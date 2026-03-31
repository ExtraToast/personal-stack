# Cloud-Init Provisioning

## Step 1 — Configure

```bash
cd infra/cloud-init
cp .env.example .env
```

Edit `.env` and fill in the required values.

### Contabo OAuth2 credentials

Visit: https://api.contabo.com/#section/Authentication

### Instance ID

```bash
source .env
cntb get instances \
  --oauth2-clientid      "${CONTABO_OAUTH2_CLIENT_ID}" \
  --oauth2-client-secret "${CONTABO_OAUTH2_CLIENT_SECRET}" \
  --oauth2-user          "${CONTABO_OAUTH2_USER}" \
  --oauth2-password      "${CONTABO_OAUTH2_PASSWORD}"
```

Copy the `instanceId` from the output into `CONTABO_INSTANCE_ID` in `.env`.

### Image ID (Ubuntu 24.04)

```bash
source .env
cntb get images \
  --oauth2-clientid      "${CONTABO_OAUTH2_CLIENT_ID}" \
  --oauth2-client-secret "${CONTABO_OAUTH2_CLIENT_SECRET}" \
  --oauth2-user          "${CONTABO_OAUTH2_USER}" \
  --oauth2-password      "${CONTABO_OAUTH2_PASSWORD}" \
  | grep -i ubuntu
```

Look for `Ubuntu 24.04` in the output and copy its `imageId` into `CONTABO_IMAGE_ID` in `.env`.

### Cloudflare DNS API Token

Traefik and Stalwart use Cloudflare DNS-01 challenges to issue certificates. You need an API token with DNS edit permissions.

1. Go to https://dash.cloudflare.com/profile/api-tokens
2. Click **Create Token**
3. Use the **Edit zone DNS** template, or create a custom token with:
   - **Permissions**: Zone / DNS / Edit
   - **Zone Resources**: Include / Specific zone / `jorisjonkers.dev`
4. Copy the token into `CF_DNS_API_TOKEN` in `.env`

### GHCR Token

The Nomad host pulls private app images from GHCR. You need a Personal Access Token (classic) with `read:packages` scope.

1. Go to https://github.com/settings/tokens
2. Click **Generate new token (classic)**
3. Select the `read:packages` scope
4. Copy the token into `GHCR_TOKEN` in `.env`

## Step 2 — Provision

```bash
./provision.sh
```

The script will:

1. Generate SSH keys (`~/.ssh/personal-stack-deploy-key` and `~/.ssh/personal-stack-root-user`) if they don't exist
2. Register the deploy key on GitHub via `gh`
3. Upload the SSH public key to Contabo and persist the Contabo secret ID back into `.env`
4. Render a cloud-init file that clones the repo and runs the full `bootstrap-server` Nomad/Vault bootstrap
5. Ask for confirmation, then reinstall the VPS via `cntb reinstall instance`

## Step 3 — Connect

After cloud-init completes:

```bash
ssh -i ~/.ssh/personal-stack-root-user -p 2222 deploy@<VPS_IP>
sudo nomad status
sudo tail -n 200 /var/log/personal-stack-bootstrap.log
```

The bootstrap stores generated control-plane credentials on the server in:

- `/opt/personal-stack/.nomad-bootstrap.env`
- `/opt/personal-stack/.vault-keys`
- `/opt/personal-stack/.nomad-keys`
