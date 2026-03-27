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

At the time of writing, the standard Contabo image ID for Ubuntu 24.04 is `afecbb85-e2fc-46f0-9684-b46b1faf00bb`.

### Cloudflare DNS API Token

Traefik uses Cloudflare DNS-01 challenges to issue wildcard TLS certificates via Let's Encrypt. You need an API token with DNS edit permissions.

1. Go to https://dash.cloudflare.com/profile/api-tokens
2. Click **Create Token**
3. Use the **Edit zone DNS** template, or create a custom token with:
   - **Permissions**: Zone / DNS / Edit
   - **Zone Resources**: Include / Specific zone / `jorisjonkers.dev`
4. Copy the token into `CF_DNS_API_TOKEN` in `.env`

### GitHub Container Registry Token

The VPS pulls private Docker images from GHCR. You need a Personal Access Token (classic) with `read:packages` scope.

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
3. Upload the SSH public key to Contabo (saves the secret ID back to `.env`)
4. Render the cloud-init template with your keys and repo URL
5. Ask for confirmation, then reinstall the VPS via `cntb reinstall instance`

## Step 3 — Connect

After a few minutes for cloud-init to complete:

```bash
ssh -i ~/.ssh/personal-stack-root-user -p 2222 deploy@<VPS_IP>
docker stack ps personal-stack
```
