# Agent GitHub App — setup

Agent runners use **two** GitHub credentials with a deliberate split:

- **Deploy key (SSH, per repo, read-write)** — git clone / fetch / push.
  Long-lived, so it never expires mid-session. It cannot destroy the
  repo: no admin scope, and the `main` ruleset blocks force-push and
  branch deletion for every actor.
- **GitHub App installation token (short-lived, repo-scoped)** — the
  one thing a deploy key cannot do: `gh pr comment` and `gh run rerun`.
  Permissions are `pull_requests:write` + `actions:write` only;
  `contents` and `administration` are never requested, so this token
  cannot push code or change settings.

assistant-api mints a fresh, single-repo installation token on demand;
the runner's `gh` wrapper fetches one before each `gh` call and caches
it for its lifetime. With the App unconfigured everything degrades
gracefully: the internal endpoint returns 503 and `gh` runs
unauthenticated.

This is a one-time setup. None of it can be done headlessly — it needs
a logged-in GitHub session — so the steps below are manual.

## 1. Create the App

Create it under whichever account owns the App (either works). To
pre-fill the permission set, save this as `create-app.html`, open it,
and click **Create GitHub App** — or set the same values by hand at
**Settings → Developer settings → GitHub Apps → New GitHub App**.

```html
<form action="https://github.com/settings/apps/new" method="post">
  <input type="hidden" name="manifest" value='{
    "name": "personal-stack-agents",
    "url": "https://github.com/ExtraToast/personal-stack",
    "public": true,
    "hook_attributes": { "active": false },
    "default_permissions": {
      "metadata": "read",
      "pull_requests": "write",
      "actions": "write"
    },
    "default_events": []
  }'>
  <button type="submit">Create GitHub App</button>
</form>
```

`public: true` is required so the **one** App can be installed on repos
under **both** ESA-Blueshell and ExtraToast. (A private App installs
only on its owner account.) Public here means "installable by other
accounts" — it grants the App access to a repo only when that account
explicitly installs it; it exposes nothing of this repo.

Leave webhooks off. Do not request `contents` or `administration`.

## 2. Generate a private key

On the App page → **Private keys → Generate a private key**. A `.pem`
downloads (PKCS#1, `BEGIN RSA PRIVATE KEY`). No conversion needed — the
minter accepts PKCS#1 and PKCS#8. Note the **App ID** shown at the top
of the page.

## 3. Install on both accounts

App page → **Install App** → install on the ExtraToast account, then on
the ESA-Blueshell account, selecting the repositories agents will work
on (or "All repositories"). assistant-api resolves the right
installation per repo owner automatically.

## 4. Store in Vault

One path holds the App id, the PEM, and a freshly generated shared
bearer (the secret the runner presents to assistant-api's internal
endpoint):

```bash
vault kv put -mount=secret agents/github-app \
  app-id="<APP_ID>" \
  private-key=@personal-stack-agents.private-key.pem \
  token-bearer="$(openssl rand -hex 32)"
```

## 5. Project the secret into both namespaces

The same `github-app` Secret is consumed in two namespaces:

- **assistant-system** — assistant-api reads `app-id`, `private-key`,
  `token-bearer` (mints tokens, verifies the bearer).
- **agents-system** — each runner reads `token-bearer` (presents it to
  assistant-api).

Project it the same way the other agent secrets are projected. For
agents-system this mirrors `credentials/kb-bearer-vss.yaml`:

```yaml
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultStaticSecret
metadata:
  name: github-app
  namespace: agents-system
spec:
  vaultAuthRef: vso-system/default
  type: kv-v2
  mount: secret
  path: agents/github-app
  destination:
    name: github-app
    create: true
  refreshAfter: 1h
```

Create the equivalent `github-app` Secret in **assistant-system** using
whatever mechanism that namespace uses for Vault-backed secrets (the
`github-api-token` Secret is provisioned the same way). All consuming
env refs are `optional: true`, so ordering does not matter — the App
simply activates once both Secrets exist.

## 6. Roll the workloads

```bash
kubectl rollout restart deploy/assistant-api -n assistant-system
```

Existing runner Pods pick up the new env on their next (re)creation; new
workspaces get it immediately.

## Verify

```bash
# From inside a runner Pod (repo-backed workspace):
gh pr comment <pr> --body "runner check"   # should post
gh run rerun <run-id>                       # should re-run

# A force-push to main must still be refused (deploy key + ruleset):
git push --force origin main                # expect: protected branch
```
