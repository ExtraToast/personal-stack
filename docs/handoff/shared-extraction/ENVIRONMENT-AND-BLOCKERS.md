# Environment, credentials & blockers — READ BEFORE GITHUB WORK

Hard-won facts about what the automation account can and cannot do here.

## GitHub identity & capability

Authenticated as the **`personal-stack-agents[bot]`** GitHub App (via `GH_TOKEN`
/ `gh auth token`; also a minting endpoint, see below). Empirically:

| Can | Cannot |
|---|---|
| Create issues, milestones, labels | **Edit** issue bodies / **comment** on issues (GraphQL `updateIssue`/`addComment` + REST PATCH all 403) |
| Create & push branches, open PRs | Push `.github/workflows/*` (see token minter) |
| Push non-workflow file contents | Apply/modify rulesets (no `administration`) |
| | Assign issues/PRs to `ExtraToast` (`replaceActorsForAssignable` 403) |

Implication: keep tracking status in `ISSUES.md` (this dossier), not by editing
GitHub issues. Cross-linking works only via `Part of #605` mentions in bodies at
creation time.

## The token minter (root cause of the workflows block)

`assistant-api` `GitHubAppInstallationTokenClient` mints short-lived, single-repo
installation tokens. It **requests a fixed permission set**, so the issued token
carries only that set even if the App is granted more. Originally
`contents`/`pull_requests`/`actions:write` → could not push workflows or edit
issues.

- **Fix:** PR **#620** widens `REQUESTED_PERMISSIONS` to add `issues:write`,
  `workflows:write`, `packages:read` (still no `administration`; `packages`
  read-only — CI publishes). Setup doc updated.
- **Activation sequence (operator):** merge #620 → widen the App's repository
  permissions on the settings page (Issues, Workflows: Read+write; Packages:
  Read) → **approve the updated permissions on each installation** → drop the
  cached token (`/tmp/.gh-app-token` in the runner Pod, or recreate the Pod) so
  the next mint is wider. Deploy assistant-api for the minter change to take
  effect.
- **Exact App permissions** (repository): Contents, Pull requests, Issues,
  Workflows, Actions, Commit statuses = **Read & write**; Packages = **Read**;
  Metadata = Read (mandatory). **Do NOT grant Administration** (rulesets are
  operator-applied) or Packages **write** (release workflow's `GITHUB_TOKEN`
  publishes).

## Push auth gotcha

The SSH remote uses a **read-only deploy key** — `git push` over SSH fails
("key is read only"). Push over **HTTPS** with the App token:
`git push https://x-access-token:$(gh auth token)@github.com/ExtraToast/<repo>.git HEAD:<branch>`.
**Scrub the token from the remote afterwards** (`git remote set-url origin <clean-url>`);
don't let it persist in `.git/config` or a tracking ref.

## Tooling gotchas

- **Codex workers cannot run gradle** (workspace-write sandbox blocks sockets) —
  gradle-gated verification must be done by a non-sandboxed agent or in CI. Plan
  fan-out accordingly.
- **Council** is configured all-Codex (`~/.claude/skills/council/council.toml`);
  never run it with `claude:opus` — it drains credits fast. Use sonnet/haiku or
  codex.
- `.council/runs/**` is **git-ignored**, so the council artifacts are copied into
  this dossier (`council-*.md/json`) to preserve them.

## repo-template state

PR `ExtraToast/repo-template#1` is open. All standard files pushed; the CI
(`ci.yml`) + release (`release.yml`) workflows are **staged** under
`.github/workflows-pending/` with an activation README, because of the workflows
block above. Activate them (and personal-stack's) once the token is fixed.
