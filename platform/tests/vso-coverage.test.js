import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import { filesUnder, readRepoText, repoPath, repoRoot, relativePath, yamlDocs } from "./_helpers.js";

test("every namespace owning a VSO secret resource is allow-listed in the vso role", async () => {
  const namespaces = await collectVsoSecretNamespaces();
  assert.ok(namespaces.length > 0, "expected to find at least one VaultStaticSecret/VaultDynamicSecret manifest under platform/cluster/flux");
  const bound = await parseVsoRoleBoundNamespaces();
  const missing = namespaces.filter(({ namespace }) => !bound.includes(namespace));
  assert.deepEqual(missing, []);
});

test("every namespace owning a VSO secret resource has a vault-secrets-operator ServiceAccount manifest", async () => {
  const namespaces = await collectVsoSecretNamespaces();
  const serviceAccountNamespaces = await collectVsoServiceAccountNamespaces();
  const missing = namespaces.filter(({ namespace }) => !serviceAccountNamespaces.has(namespace));
  assert.deepEqual(missing, []);
});

async function collectVsoSecretNamespaces() {
  const fluxApps = repoPath("platform/cluster/flux/apps");
  const files = (await filesUnder(fluxApps)).filter((file) => [".yaml", ".yml"].includes(path.extname(file)));
  const refs = [];
  for (const file of files) {
    for (const doc of yamlDocs(await readRepoText(relativePath(repoRoot, file)))) {
      if (doc.kind === "VaultStaticSecret" || doc.kind === "VaultDynamicSecret") {
        if (doc.metadata?.namespace) refs.push({ namespace: doc.metadata.namespace, sourcePath: relativePath(repoRoot, file) });
      }
    }
  }
  return refs;
}

async function collectVsoServiceAccountNamespaces() {
  const fluxApps = repoPath("platform/cluster/flux/apps");
  const files = (await filesUnder(fluxApps)).filter((file) => [".yaml", ".yml"].includes(path.extname(file)));
  const namespaces = new Set();
  for (const file of files) {
    for (const doc of yamlDocs(await readRepoText(relativePath(repoRoot, file)))) {
      if (doc.kind === "ServiceAccount" && doc.metadata?.name === "vault-secrets-operator" && doc.metadata?.namespace) {
        namespaces.add(doc.metadata.namespace);
      }
    }
  }
  return namespaces;
}

async function parseVsoRoleBoundNamespaces() {
  const bootstrap = await readRepoText("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh");
  const roleBlock = /vault\s+write\s+auth\/kubernetes\/role\/vso\s*\\?\n((?:\s*[^\n]+\\?\n)+)/.exec(bootstrap)?.[1];
  assert.ok(roleBlock, "could not find `vault write auth/kubernetes/role/vso` block in bootstrap-auth.sh");
  const value = /bound_service_account_namespaces="([^"]+)"/.exec(roleBlock)?.[1];
  assert.ok(value, "vso role block has no bound_service_account_namespaces=\"...\" line");
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}
