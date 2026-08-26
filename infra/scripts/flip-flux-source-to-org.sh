#!/usr/bin/env bash
###############################################################################
# Flip the live Flux source to JorisJonkers-dev/fleet-infra @ deploy/production.
#
# The secret and the GitRepository must change together: source-controller
# rejects a secret carrying GitHub App keys unless provider is github, and
# rejects provider github unless the keys are present. Either half alone leaves
# the source InvalidProviderConfiguration, so this does both back to back.
#
# Usage: bash infra/scripts/flip-flux-source-to-org.sh
# Undo:  bash flip-flux-source-to-monorepo.sh
###############################################################################
set -uo pipefail

CTX="${KUBE_CONTEXT:-personal}"
APP_ID=3942418
INSTALL_ID=142960823
EXPECT_URL="https://github.com/JorisJonkers-dev/fleet-infra"
EXPECT_BRANCH="deploy/production"
WORK="${WORK:-/tmp/flip-org-$$}"

k() { kubectl --context "$CTX" "$@"; }
die() { echo "ABORT: $*" >&2; exit 1; }

echo "== 1. checkout deploy/production =="
rm -rf "$WORK"
git clone -q --depth 1 --branch "$EXPECT_BRANCH" "$EXPECT_URL" "$WORK" \
  || die "cannot clone $EXPECT_URL ($EXPECT_BRANCH)"
cd "$WORK" || die "cannot cd $WORK"
echo "   $(git log --oneline -1)"

echo "== 2. verify the manifest before applying it =="
SYNC=cluster/flux/clusters/production/flux-system/gotk-sync.yaml
[ -f "$SYNC" ] || die "$SYNC missing"
grep -q "provider: github" "$SYNC" || die "gotk-sync has no 'provider: github'; the App keys would be rejected"
grep -q "$EXPECT_URL" "$SYNC"     || die "gotk-sync does not point at $EXPECT_URL"
grep -q "branch: $EXPECT_BRANCH"  "$SYNC" || die "gotk-sync does not target $EXPECT_BRANCH"
echo "   provider: github, url and branch as expected"

# apps-edge pruning must be off for the flip: 38 public IngressRoutes move to
# other Kustomizations, nothing orders apps-edge after the ones adopting them,
# and a prune-first reconcile drops public routing until they catch up.
PRUNE=$(python3 -c "
import yaml
for d in yaml.safe_load_all(open('cluster/flux/clusters/production/kustomizations.yaml')):
    if d and d.get('kind')=='Kustomization' and d['metadata']['name']=='apps-edge':
        print(d['spec'].get('prune'))
" 2>/dev/null)
if [ "$PRUNE" = "False" ]; then
  echo "   apps-edge prune: false (route relocation is safe)"
else
  echo "   WARNING: apps-edge prune=$PRUNE. Public routes may drop briefly during the flip."
fi

echo "== 3. add the GitHub App credentials to the flux-system secret =="
KEY_B64=$(k get secret -n agents-system github-app -o jsonpath='{.data.private-key}' 2>/dev/null)
[ -n "$KEY_B64" ] || die "cannot read agents-system/github-app private-key"
ID_B64=$(printf '%s' "$APP_ID" | base64)
INST_B64=$(printf '%s' "$INSTALL_ID" | base64)
k patch secret flux-system -n flux-system --type merge \
  -p "{\"data\":{\"githubAppPrivateKey\":\"$KEY_B64\",\"githubAppID\":\"$ID_B64\",\"githubAppInstallationID\":\"$INST_B64\"}}" \
  || die "secret patch failed"
echo "   NOTE: the live source is invalid from here until step 4 applies provider: github."

echo "== 4. apply the new source =="
k apply -k cluster/flux/clusters/production/flux-system || die "apply failed -- run flip-flux-source-to-monorepo.sh"

echo "== 5. reconcile =="
flux --context "$CTX" reconcile source git flux-system -n flux-system --timeout=180s || true
flux --context "$CTX" reconcile kustomization flux-system -n flux-system --timeout=300s || true

echo "== 6. verify =="
k get gitrepository -n flux-system flux-system \
  -o jsonpath='   url={.spec.url}{"\n"}   branch={.spec.ref.branch}{"\n"}   provider={.spec.provider}{"\n"}   ready={.status.conditions[?(@.type=="Ready")].status}{"\n"}   artifact={.status.artifact.revision}{"\n"}'
echo
echo "   Kustomizations not ready:"
k get kustomization -n flux-system -o json \
  | python3 -c 'import json,sys
for k in json.load(sys.stdin)["items"]:
    c=[x for x in (k.get("status") or {}).get("conditions") or [] if x["type"]=="Ready"]
    if c and c[0]["status"]!="True": print("     ",k["metadata"]["name"],"-",c[0].get("reason"))'
echo "   pods not Running/Completed:"
k get pods -A --no-headers | awk '$4!="Running" && $4!="Completed" {print "      "$1"/"$2"  "$4}' | head -15
echo "   PVCs (expect 24):" "$(k get pvc -A --no-headers | wc -l | tr -d ' ')"
echo
echo "Done. If this went wrong: bash flip-flux-source-to-monorepo.sh"
