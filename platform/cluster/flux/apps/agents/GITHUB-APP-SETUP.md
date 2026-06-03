# Agent GitHub App — setup

Agent runners use **two** GitHub credentials with a deliberate split:

- **Deploy key (SSH, per repo)** — boot-time clone/fetch fallback. Long-
  lived, so a workspace can still clone even when the short-lived token
  broker is unavailable.
- **GitHub App installation token (short-lived, repo-scoped)** — the
  workspace write path: `git push`, `gh pr create`, `gh pr comment`,
  and `gh run rerun`. Permissions are `contents:write`,
  `pull_requests:write`, and `actions:write` only; `administration` is
  never requested, so this token cannot change repo settings. The
  `main` ruleset still blocks force-push and branch deletion.

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
  <input
    type="hidden"
    name="manifest"
    value='{
    "name": "personal-stack-agents",
    "url": "https://github.com/ExtraToast/personal-stack",
    "public": true,
    "hook_attributes": { "active": false },
    "default_permissions": {
      "metadata": "read",
      "contents": "write",
      "pull_requests": "write",
      "actions": "write"
    },
    "default_events": []
  }'
  />
  <button type="submit">Create GitHub App</button>
</form>
```

`public: true` is required so the **one** App can be installed on repos
under **both** ESA-Blueshell and ExtraToast. (A private App installs
only on its owner account.) Public here means "installable by other
accounts" — it grants the App access to a repo only when that account
explicitly installs it; it exposes nothing of this repo.

Leave webhooks off. Do not request `administration`.

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
git push -u origin <branch>                 # should create/update the branch
gh pr create --draft --fill                 # should create the PR
gh pr comment <pr> --body "runner check"    # should post
gh run rerun <run-id>                       # should re-run

# A force-push to main must still be refused (App token + ruleset):
git push --force origin main                # expect: protected branch
```

## Troubleshooting: token has no push/pull/Actions access

Symptom: the runner's `gh auth status` shows `personal-stack-agents[bot]`,
but `git push` / `gh pr create` / `gh run rerun` behave read-only, and
`gh api /installation/repositories` lists the repo with every
`permissions` boolean `false`.

Cause: GitHub mints an installation token scoped to **whatever the
installation actually holds**, silently dropping anything the App was
not granted. assistant-api always _requests_ `contents`,
`pull_requests`, and `actions` at `write`
(`GitHubAppInstallationTokenClient.REQUESTED_PERMISSIONS`), so a
narrowed token means the **App itself** is under-permissioned — almost
always one of:

1. The App was created by hand (not via the manifest in §1), so it only
   has GitHub's default `metadata: read`.
2. The App's permissions were widened later, but the installation has a
   **pending approval**. Widening an App's permission set does **not**
   apply to existing installations until each one approves the request.

assistant-api logs this exact case at WARN on the next mint:

```
installation token for <owner>/<repo> is missing requested permissions
[actions, contents, pull_requests] (granted: {metadata=read}). Widen the
personal-stack-agents App's repository permissions … then approve …
```

Fix:

1. App settings → **Permissions → Repository permissions**: set
   Metadata: Read-only, **Contents: Read and write**, **Pull requests:
   Read and write**, **Actions: Read and write**. Save.
2. For **each** account the App is installed on (ExtraToast and
   ESA-Blueshell), open the installation and **approve the updated
   permissions** (GitHub surfaces a "review request" banner; an org
   install may need an org owner to approve).
3. Drop the cached token so the next call re-mints with the wider grant:
   delete `/tmp/.gh-app-token` in the runner Pod, or recreate the Pod.
   No assistant-api restart is needed — the permission set is requested
   per mint, not cached.

The `permissions` booleans on `/installation/repositories` stay `false`
even after the fix — those describe a _user's_ collaborator level and
are always false for an App token. Confirm with a real operation
(`git push` a throwaway branch, `gh pr create --draft`), not that field.
