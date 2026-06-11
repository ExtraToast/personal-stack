import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import { filesUnder, readRepoText, repoPath, repoRoot, relativePath } from "./_helpers.js";

test("every spring vault database role referenced by a service has a matching bootstrap role", async () => {
  const roles = await collectDeclaredVaultDatabaseRoles();
  assert.ok(roles.length > 0, "expected to find at least one spring.cloud.vault.database.role declaration in a service application.yml");
  const bootstrap = await readRepoText("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh");
  const missing = roles.filter(([, role]) => !bootstrap.includes(`vault write database/roles/${role}`));
  assert.deepEqual(missing, []);
});

test("every referenced role is listed in allowed_roles on database config postgres", async () => {
  const roles = await collectDeclaredVaultDatabaseRoles();
  const bootstrap = await readRepoText("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh");
  const allowedRolesLine = bootstrap.split("\n").find((line) => line.includes("allowed_roles="));
  assert.ok(allowedRolesLine, "no allowed_roles= line found in bootstrap-auth.sh");
  const allowed = /allowed_roles="([^"]+)"/.exec(allowedRolesLine)?.[1]?.split(",").map((item) => item.trim());
  assert.ok(allowed, `could not parse allowed_roles value out of: ${allowedRolesLine}`);
  const notAllowed = roles.map(([, role]) => role).filter((role) => !allowed.includes(role));
  assert.deepEqual(notAllowed, []);
});

async function collectDeclaredVaultDatabaseRoles() {
  const serviceFiles = (await filesUnder(repoPath("services")))
    .filter((file) => path.basename(file) === "application.yml" && relativePath(repoRoot, file).includes("/src/main/resources/"));
  const roles = [];
  for (const file of serviceFiles) {
    const role = await extractDatabaseRole(file);
    if (role) roles.push([relativePath(repoRoot, file).split("/")[1], role]);
  }
  return roles;
}

async function extractDatabaseRole(file) {
  const text = await readRepoText(relativePath(repoRoot, file));
  const databaseBlock = /spring:\s*\n(?:.*\n)*?\s+cloud:\s*\n(?:.*\n)*?\s+vault:\s*\n(?:.*\n)*?\s+database:\s*\n((?:\s+.+\n)+)/m.exec(text)?.[1];
  if (!databaseBlock) return null;
  const roleLine = databaseBlock.split("\n").find((line) => line.trim().startsWith("role:"));
  if (!roleLine) return null;
  const raw = roleLine.split("role:")[1].trim();
  return /\$\{[^:}]+:([^}]+)\}/.exec(raw)?.[1]?.trim() ?? raw.replace(/^["']|["']$/g, "");
}
