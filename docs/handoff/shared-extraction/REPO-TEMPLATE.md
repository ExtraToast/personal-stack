# repo-template — the operator-added template repository

`ExtraToast/repo-template` is the repo every ExtraToast repository is
bootstrapped from. It was added at the operator's request ("a template
repository for future repos that has important things such as rulesets, and new
PR and issue templates... consider what else to put into such a template repo").
Built in PR `ExtraToast/repo-template#1`. This file mirrors its contents and the
decisions so the dossier is self-contained; the repo itself is the source of
truth.

## What it contains

| Path | Purpose |
|---|---|
| `.github/workflows/ci.yml` | The canonical single CI pipeline ending in the **`Pipeline Complete`** aggregator job (`if: always()` + `re-actors/alls-green` over `needs`). The only required check. *(staged in `.github/workflows-pending/` until the workflows-token fix; see ENVIRONMENT-AND-BLOCKERS.md)* |
| `.github/workflows/release.yml` | Tag→release via release-please; published-release event publishes the artifact (Maven/npm/image). *(also staged)* |
| `release-please-config.json`, `.release-please-manifest.json` | Versioning state for release-please. |
| `.github/rulesets/main.json` | The common branch ruleset **as code** — faithful to the operator's supplied ruleset: `deletion` + `non_fast_forward` + `required_linear_history`, pull_request (squash-only `allowed_merge_methods`, 0 approvals), and `required_status_checks` = **`Pipeline Complete`** (integration_id 15368). |
| `scripts/apply-ruleset.sh` | Idempotent apply of that ruleset to a repo (create-or-update). **Operator-run** — the agent token has no `administration`. |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR template: What/Why, **Tracking** (`Closes #` / `Part of #`), Verification (incl. `Pipeline Complete` green + tracking issue updated), Versioning impact. Impersonal voice. |
| `.github/ISSUE_TEMPLATE/{bug_report,feature_request,task}.yml` + `config.yml` | Bug / feature / task forms; blank issues disabled; contact link to CONTRIBUTING. |
| `.github/CODEOWNERS` | `* @ExtraToast`. |
| `renovate.json` | Exact-pin (`rangeStrategy: pin`), groups `dev.extratoast.*` / `@extratoast/*` / `ExtraToast/github-workflows` into one platform bump, pins action digests, `minimumReleaseAge`. |
| `.github/dependabot.yml` | GitHub-Actions security-update fallback (Renovate is primary). |
| `SECURITY.md` | Private reporting; gitleaks in CI; secrets from Vault; latest-version-only support. |
| `CONTRIBUTING.md` | Branch/PR flow, squash-only + linear history, the one-pipeline/`Pipeline Complete` rule, versioning, commit/PR voice, tracking discipline. |
| `VERSIONING.md` | Canonical statement of the tag→release + exact-pin consumption + version-pinned deploy model (mirrored in this dossier's VERSIONING.md). |
| `docs/REPO_SETUP.md` | How to bootstrap a new repo from the template + a file-by-file table. |
| `.editorconfig`, `.gitignore`, `.gitleaks.toml`, `LICENSE`, `README.md` | Baseline hygiene + proprietary license matching personal-stack. |

## "What else to put in it" — decisions made

Included beyond the operator's explicit asks (rulesets, PR/issue templates):
release-please + manifest (versioning is an org requirement), Renovate (exact
pins), CODEOWNERS, SECURITY, CONTRIBUTING, VERSIONING, REPO_SETUP, gitleaks,
editorconfig, gitignore, LICENSE, and the ruleset-as-code + apply script.

Deliberately **excluded**: language/stack-specific build files (each repo adds
its own — JVM/Gradle vs npm vs Nix differ), and any `administration`-requiring
automation (rulesets are operator-applied). The `ci.yml` jobs are placeholders
(`lint`/`test`/`build`) to be replaced per repo or wired to reusable workflows
from `ExtraToast/github-workflows` once that exists — but the `pipeline-complete`
aggregator must always remain and list every gating job in `needs:`.

## How every repo uses it

1. Create the repo from the template (`gh repo create … --template ExtraToast/repo-template`).
2. Operator runs `scripts/apply-ruleset.sh <owner/repo>` so `Pipeline Complete` is required.
3. Replace placeholder CI jobs with the real ones (keep the aggregator).
4. Set the release artifact + starting version.
5. Enable Renovate; adjust CODEOWNERS/README.

Tracking: bootstrapping/ruleset rollout is issue **#607**.
