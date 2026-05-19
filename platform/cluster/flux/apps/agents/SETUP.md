# Bootstrapping the agents subsystem

Step-by-step for getting from a fresh cluster (with the `apps-agents`
Flux Kustomization reconciled) to a state where every workspace can
spin up runner Pods that are already logged in to Claude.ai + ChatGPT
without ever shipping an API key.

The whole process is **one manual session** on the laptop. After it,
the cluster maintains itself: the refresh-ping CronJob exercises both
CLIs every six hours, OAuth refresh tokens rotate in place on the
PVCs, and per-Project deploy keys are managed via the assistant-ui
wizard.

## 0. Prerequisites

Once-only, before doing anything in the cluster:

- [ ] **Email forwarding.** `claude@jorisjonkers.dev` and
      `codex@jorisjonkers.dev` need to deliver into a mailbox you can
      open during the bootstrap (the OAuth flows send a short
      verification code there). Test forwarding by sending yourself a
      message to each address and confirming it lands.

- [ ] **Claude Pro / Max subscription** active on the address you
      will use as the Claude login (`claude@jorisjonkers.dev` or
      whichever you've decided). The CLI's `/login` flow opens
      claude.ai in a laptop browser and will only complete if that
      account has an active subscription. (API keys aren't used —
      this is the whole point.)

- [ ] **ChatGPT Plus / Pro / Business / Edu / Enterprise**
      subscription on the address used for Codex. The free tier does
      not include Codex CLI access.

- [ ] **Cluster context selected.**
      `    kube-personal
kubectl get ns agents-system`
      should show `Active`. If it doesn't, the `apps-agents` Flux
      Kustomization hasn't reconciled yet — `flux reconcile
kustomization apps-agents --timeout=60s` to nudge it.

## 1. Populate the knowledge-system bearer token

Every runner Pod inherits a copy of the Claude Code hooks + skills
that pair with `knowledge-api`'s MCP server. The
`agents-kb-install` CronJob runs `curl … /install.sh | bash` from
inside the cluster against the shared `claude-credentials` PVC,
which means a single install covers every workspace.

The installer authenticates with a bearer token. Mint one and put
it in Vault:

```sh
TOKEN="$(openssl rand -hex 32)"
vault kv put -mount=secret agents/kb-bearer bearer="$TOKEN"
# And register the same token under the MCP server's allow-list:
vault kv patch -mount=secret knowledge-system/mcp-bearer \
  agents="$TOKEN"
```

The Vault Static Secret operator projects this into the
`agents-kb-bearer` Secret in `agents-system` within
`refreshAfter: 1h`.

Trigger the first install on demand (the CronJob also runs
weekly):

```sh
kubectl -n agents-system create job --from=cronjob/agents-kb-install kb-install-now
kubectl -n agents-system logs -f job/kb-install-now
```

Look for "[kb-install] done" plus `hooks/` and `skills/` listings.
Clean up:

```sh
kubectl -n agents-system delete job kb-install-now
```

## 2. Provision the shared ad-hoc deploy key (optional)

Skip this if you only ever plan to use Project-backed workspaces (the
recommended path). The shared key is the fallback that ad-hoc
"Repo URL" workspaces use — convenient for one-off pokes at public
repos, but a wider blast radius than the per-link Project keys.

Generate an ed25519 key pair on your laptop:

```sh
ssh-keygen -t ed25519 -f ./ps-agents-deploy -C "ps-agents-shared@personal-stack" -N ""
```

Add the **public key** as a deploy key under a personal-stack-owned
repo (e.g. a sandbox repo with no production data). On GitHub:
**Settings → Deploy keys → Add deploy key**, leave write-access
**unchecked** — the shared key is read-only by policy.

Push the private/public key to Vault:

```sh
vault kv put -mount=secret agents/github-deploy-key \
  private_key="$(cat ./ps-agents-deploy)" \
  public_key="$(cat ./ps-agents-deploy.pub)" \
  known_hosts="$(ssh-keyscan github.com 2>/dev/null)"
```

The Vault Static Secret operator will project these into the
`agents-github-deploy-key` Secret in the `agents-system` namespace
within `refreshAfter: 1h` (force it sooner with `kubectl annotate
vss/agents-github-deploy-key force-sync=$(date +%s) -n agents-system`).

You can delete the local `./ps-agents-deploy*` files after Vault has
the bytes — keep a copy in 1Password if you want a personal backup.

## 3. Bring up the auth-bootstrap Pod

This Pod exists already if `apps-agents` has reconciled. Sanity-check:

```sh
kubectl -n agents-system get pod auth-bootstrap
```

Expected state: `Running`. If it's not there, force a reconcile:

```sh
flux reconcile kustomization apps-agents --timeout=60s
kubectl -n agents-system wait --for=condition=Ready pod/auth-bootstrap --timeout=120s
```

If the Pod is in `ImagePullBackOff`, the `ghcr.io/extratoast/personal-stack/agent-runner:latest`
image hasn't been built yet — push the branch through CI, wait for
Keel to pull, and retry.

## 4. Log Claude Code in

Open a shell inside the bootstrap Pod:

```sh
kubectl -n agents-system exec -it auth-bootstrap -- bash
```

You should see a `agent@auth-bootstrap:/$` prompt. Confirm both CLIs
are on PATH:

```sh
claude --version
codex --version
```

Now run the Claude login flow:

```sh
claude /login
```

The CLI prints something like:

```
Open this URL in a browser to log in:

  https://claude.ai/oauth/authorize?...

After authorising, paste the redirect URL back here:
```

Do the following:

1. **Copy the entire URL** the CLI prints. Don't truncate it.
2. **Open it on your laptop browser**, signed in to your Claude Pro
   account.
3. Claude.ai asks you to **confirm the device login**. You may also
   get a verification code emailed to the account address — check
   `claude@jorisjonkers.dev`.
4. **Approve.** Claude redirects to a URL like
   `https://claude.ai/oauth/callback?code=…&state=…`. The browser
   page will show that URL in the address bar (or display a
   "Copy this URL" affordance, depending on the build).
5. **Copy the full redirect URL** and paste it back into the
   `auth-bootstrap` terminal.

Verify it took:

```sh
ls -la ~/.claude/.credentials.json    # should exist, non-empty
claude -p 'reply with the single word pong' --output-format text
```

If both succeed, Claude is wired.

## 5. Log Codex CLI in

In the same shell:

```sh
codex login --device
```

The device-code flow looks like:

```
To complete sign in, visit:

  https://chatgpt.com/codex/auth

and enter the code:

  ABCD-EFGH

Waiting for confirmation…
```

Do this:

1. **Open the URL on your laptop**, signed in to the ChatGPT account
   with the Codex-enabled subscription.
2. **Enter the code** exactly as shown (including the dash).
3. ChatGPT asks for confirmation; approve. A verification code may
   land in `codex@jorisjonkers.dev` — paste it if asked.
4. The terminal will unblock and print a success line.

Verify:

```sh
ls -la ~/.codex/auth.json             # should exist, non-empty
codex exec 'reply with the single word pong'
```

If both succeed, Codex is wired.

Exit the Pod:

```sh
exit
```

## 6. Confirm the refresh-ping CronJob is happy

The `agents-refresh-ping` CronJob runs every six hours and re-runs the
same sanity prompts. Trigger one immediately so you can verify both
CLIs work from a _fresh_ Pod (not the one you just typed into):

```sh
kubectl -n agents-system create job --from=cronjob/agents-refresh-ping refresh-ping-manual
kubectl -n agents-system logs -f job/refresh-ping-manual
```

Expected output:

```
[ping] claude --version
1.x.x …
[ping] claude -p (one-shot)
pong
[ping] codex --version
0.x.x …
[ping] codex exec (one-shot)
pong
[ping] both CLIs answered
```

If either CLI prints "Not logged in" or a 401, the credential PVC
isn't where the runner expects to find it — re-check that you ran
the `claude /login` / `codex login --device` from the bootstrap Pod
(which mounts the same PVCs), not from your laptop.

Clean up the manual job:

```sh
kubectl -n agents-system delete job refresh-ping-manual
```

## 7. Spin up your first workspace

In assistant-ui at `https://assistant.jorisjonkers.dev` (or whichever
host the forward-auth fronted UI lives on):

1. Go to **Projects** → **New project**. Give it a name and an
   auto-derived slug.
2. **Add repo** under the new project. Paste the GitHub repo URL
   (ssh or https).
3. Click **Attach key**. The wizard renders the deploy-key setup
   guide inline. Follow the three steps:
   - `ssh-keygen -t ed25519 -f ./<name>-deploy -N ""` on your
     laptop.
   - Paste the contents of `<name>-deploy.pub` into the linked
     repo's **Settings → Deploy keys → Add deploy key** page.
     Check "Allow write access" if you want agents to be able to
     push.
   - Paste both halves of the key back into the wizard and submit.
4. Go to **Workspaces** → **New workspace**. Pick **Project repo**,
   choose your new project + link, give it a name, **Create**.
5. In the workspace view, pick **Claude Code** or **Codex** and
   click **New agent**. The first response should arrive within a
   few seconds; the runner Pod is already booted and the CLI is
   already logged in.

## 8. When things go wrong

- **`claude /login` fails with "Address not allowed"** — the
  OAuth-redirect form requires the URL to come from `claude.ai`.
  Make sure you copied the _redirect_ URL (after approving in the
  browser), not the original authorization URL.
- **`codex login --device` hangs forever** — most often the device
  code expired (~10 min). Cancel with Ctrl-C, re-run, and complete
  faster.
- **Refresh-ping starts failing weeks later** — refresh tokens can
  rotate out of sync with the on-disk file under network jitter.
  Re-run the bootstrap (`claude /login` / `codex login --device`)
  from the bootstrap Pod. Both CLIs preserve existing credentials,
  so this is a no-data-loss operation.
- **Workspace Pod is stuck `ContainerCreating`** — `kubectl describe`
  the Pod; usually it's a Secret mount waiting on Vault Static
  Secret Operator. Force a sync on `vss/agents-github-deploy-key`.
