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

## Re-authenticating Claude and Codex credentials

There are two ways to refresh the runner credentials when they expire.
Prefer the **login portal**; the **terminal stopgap** is the fallback
for when the portal is not yet deployed.

### Login portal (preferred)

`agents-login.jorisjonkers.dev` is a browser-driven portal that runs
`claude setup-token` and `codex login --device` server-side and posts the
captured OAuth credentials to agents-api, which persists them in Postgres
for runner consumption — so one re-sign-in renews the whole fleet and no
text has to be copied out of a terminal. It is forward-auth protected by a
dedicated `AGENTS_LOGIN` permission, NOT the shared `AGENTS` one: a
holder of that permission can mint the fleet's root credentials, so it
is granted deliberately.

The portal is two Deployments in `agents-system`:

- **controller** — the public UI. Holds no credential store access and stores no
  credential files. Uses short HTTP polling (not a WebSocket: the
  Cloudflare edge kills idle sockets ~100 s and the OAuth approval idles
  longer) and proxies the flow to the worker over an internal token.
- **worker** — owns the PTY that runs the CLI login, captures the
  credentials, and posts them to agents-api under a single-writer Kubernetes
  `Lease` (`agents-login-write`). The worker uses the same
  `agents-login-internal` Secret token that agents-api uses for worker calls.

To re-authenticate: open the portal, start the Claude login (copy the
authorize URL into a browser tab signed in to the Claude Pro account,
approve, paste the redirect URL back into the page), then start the
Codex device login (enter the displayed code at the OpenAI device page).

### Terminal stopgap (until the portal is deployed)

Re-auth from the bootstrap Pod — but run the exec from the operator's
OWN workstation terminal, which can copy text, NOT the agents-ui xterm
relay:

```sh
kubectl -n agents-system exec -it auth-bootstrap -- bash
claude /login          # paste the authorize URL out, paste the redirect back
codex login --device   # enter the device code at chatgpt.com/codex/auth
```

`kubectl logs auth-bootstrap` is NOT a copy channel: the Pod runs `sleep
infinity`, a later exec session's stdout is not the Pod's logs, and the
Claude flow needs the redirect URL fed back on stdin.

## agents-api credential ingest

The worker calls agents-api's internal ingest endpoint:

- `POST http://agents-api.agents-system.svc.cluster.local:8082/api/v1/internal/credentials`
- `Authorization: Bearer <agents-login-internal token>`
- Body: `userId`, uppercase `provider`, and a provider payload.

Claude payloads contain `oauth_token`. Codex payloads contain `auth_json`
and `config_toml`. agents-api owns persistence in Postgres; the worker does
not read back stored credential status.

The shared controller↔worker token lives at
`secret/agents/login-internal-token` (field `token`) and is projected into
the Kubernetes Secret `agents-login-internal`. Seed it once:

```sh
vault kv put -mount=secret agents/login-internal-token token="$(openssl rand -hex 32)"
```

## Writeback and failure modes

The worker is the single writer that posts captured credentials to agents-api,
serialized by the `agents-login-write` Lease:

- **No captured credential** — the session fails and nothing is posted.
- **agents-api ingest failure** — the session fails and nothing is persisted by
  the worker.
- **Worker down** — credentials go stale until the next login; the
  refresh-ping CronJob is the early-warning signal.
- **Two writers** — prevented by `replicas: 1` + `Recreate` + the Lease.

## Node-pin classification

Moving credentials into Postgres does NOT by itself let runners schedule
anywhere. `AGENT_RUNTIME_NODE=enschede-gtx-960m-1` is **multi-reason**:
the docker-socket GID, the hard-coded egress callback IP in
`network-policy/runner-egress.yaml`, and the untrusted-workloads-at-home
policy. Only the **credential** justification is removed by the credential-store
migration; the others keep the pin until the companion runner change
tolerates capability scheduling. The agents-login worker itself is **not**
pinned — it posts to agents-api, so it runs anywhere.

## Companion change (agents repo)

Making runners consume agents-api-stored credentials instead of the PVCs is a
companion change in the `agents` repo:

- `services/agent-runner/Dockerfile` + `/opt/entrypoint.sh` — create a
  writable `$HOME/.claude` / `$CODEX_HOME` and materialize the fetched
  credential files into place.
- `Fabric8AgentRunnerOrchestrator.kt` — stop mounting the two
  `claude-credentials` / `codex-credentials` PVC volume sources once runners
  can fetch credentials through agents-api.

Until that lands, runners still read the PVCs, so the legacy bootstrap
(sections 3-5 below) and the `claude-credentials` / `codex-credentials`
PVCs stay in place. Watch for **image skew**: the portal, the
agent-runner image, and the orchestrator-launched runner image must share
a compatible credential-file format, so converge the bundled CLI versions
before any cutover.

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
codex exec --skip-git-repo-check 'reply with the single word pong' </dev/null
```

If both succeed, Codex is wired.

### 5a. Remove the GitHub connector from the ChatGPT account

**Required hardening — do not skip.** Codex surfaces every connector
authorized on the signed-in ChatGPT account as `codex_apps.*` tools.
The GitHub connector among them authenticates as that account's own
GitHub OAuth identity, so it inherits **every org that identity can
see** — including any employer org the account holder belongs to — and
bypasses the repo-scoped GitHub App installation token entirely. An
agent running Codex would be able to search and read those private
repos. The only sanctioned GitHub access for an agent is the `github`
MCP server (a short-lived, repo-scoped App installation token).

The runner forces `features.apps = false` in `~/.codex/config.toml` on
every boot (see `services/agent-runner/entrypoint.sh`), which gates the
connector subsystem off. Treat that as defence-in-depth only: per-app
disables have been ignored upstream (openai/codex#17588), so the hard
guarantee is to remove the connector at the account:

1. Sign in to ChatGPT as the Codex account on a laptop.
2. **Settings → Connectors / Apps → GitHub → Disconnect.**
3. Revoke the grant at `https://github.com/settings/applications` (and,
   if the account belongs to an org with OAuth-app restrictions, confirm
   it is not re-approved there).
4. Re-run the verify step below from a fresh Pod and confirm no
   `codex_apps`/GitHub-connector tools are listed while the declared
   `github` MCP server (App token) still works.

Keep the Codex account free of GitHub (and any other sensitive)
connectors going forward — re-adding one re-opens this path.

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
