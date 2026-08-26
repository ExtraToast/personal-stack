#!/usr/bin/env bash
###############################################################################
# Roll the live Flux source back to ExtraToast/personal-stack @ main.
#
# Three steps, not two. kubectl apply does NOT remove spec.provider, so
# applying the monorepo manifest while provider: github lingers leaves the
# source rejecting itself with the inverse error:
#   "secretRef with github app data must be specified when provider is github"
# That is what happened on the first rollback, so the field is deleted
# explicitly here.
#
# Usage: bash infra/scripts/flip-flux-source-to-monorepo.sh
#        MONOREPO=/path/to/personal-stack bash flip-flux-source-to-monorepo.sh
###############################################################################
set -uo pipefail

CTX="${KUBE_CONTEXT:-personal}"
# infra/scripts/<this> -> repo root is two levels up. Overridable for a clone
# checked out elsewhere.
MONOREPO="${MONOREPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
EXPECT_URL="https://github.com/ExtraToast/personal-stack"
SYNC="platform/cluster/flux/clusters/production/flux-system/gotk-sync.yaml"

k() { kubectl --context "$CTX" "$@"; }
die() { echo "ABORT: $*" >&2; exit 1; }

echo "== 1. verify the monorepo manifest =="
[ -f "$MONOREPO/$SYNC" ] || die "$MONOREPO/$SYNC not found (set MONOREPO=...)"
grep -q "$EXPECT_URL" "$MONOREPO/$SYNC" || die "$SYNC does not point at $EXPECT_URL"
grep -q "provider:" "$MONOREPO/$SYNC" && die "$SYNC unexpectedly sets provider; review before rolling back"
echo "   points at $EXPECT_URL, no provider field"

echo "== 2. drop the GitHub App credentials =="
# Ignore failures: the keys may already be absent, which is fine.
k patch secret flux-system -n flux-system --type=json -p '[
  {"op":"remove","path":"/data/githubAppID"},
  {"op":"remove","path":"/data/githubAppInstallationID"},
  {"op":"remove","path":"/data/githubAppPrivateKey"}]' 2>/dev/null \
  && echo "   removed" || echo "   already absent"

echo "== 3. drop spec.provider from the live GitRepository =="
k patch gitrepository flux-system -n flux-system --type=json \
  -p '[{"op":"remove","path":"/spec/provider"}]' 2>/dev/null \
  && echo "   removed" || echo "   already absent"

echo "== 4. apply the monorepo source =="
cd "$MONOREPO" || die "cannot cd $MONOREPO"
k apply -k platform/cluster/flux/clusters/production/flux-system || die "apply failed"

echo "== 5. reconcile =="
flux --context "$CTX" reconcile source git flux-system -n flux-system --timeout=180s || true
flux --context "$CTX" reconcile kustomization flux-system -n flux-system --timeout=300s || true

echo "== 6. verify =="
k get gitrepository -n flux-system flux-system \
  -o jsonpath='   url={.spec.url}{"\n"}   branch={.spec.ref.branch}{"\n"}   provider={.spec.provider}{"\n"}   ready={.status.conditions[?(@.type=="Ready")].status}{"\n"}   artifact={.status.artifact.revision}{"\n"}'
echo
echo "   Reconciling the services most likely to have been mid-roll:"
for ks in apps-mail apps-utility-system apps-stateless; do
  flux --context "$CTX" reconcile kustomization "$ks" --timeout=180s >/dev/null 2>&1 \
    && echo "      $ks reconciled" || echo "      $ks FAILED"
done
echo "   pods not Running/Completed:"
k get pods -A --no-headers | awk '$4!="Running" && $4!="Completed" {print "      "$1"/"$2"  "$4}' | head -15
echo
echo "KNOWN: agents-api cannot roll back. The database is at schema 23, created"
echo "by v0.19.1; the monorepo image knows 20 migrations and exits instead of"
echo "running against a newer schema. Its healthy v0.19.1 pod keeps serving, but"
echo "the Deployment's desired state is the broken image."
