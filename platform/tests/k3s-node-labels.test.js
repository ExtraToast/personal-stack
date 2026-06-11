import test from "node:test";
import { loadFleet, readRepoText, assertContains } from "./_helpers.js";

test("k3s host definitions expose inventory derived node labels", async () => {
  const fleet = await loadFleet();
  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    if (!node.target_roles.some((role) => role.startsWith("k3s-"))) continue;
    const hostDefinition = await readRepoText("platform/nix/hosts", nodeName, "default.nix");
    assertContains(
      hostDefinition,
      "personalStack.k3sNodeLabels",
      `"personal-stack/site" = "${node.site}"`,
      `"personal-stack/node" = "${nodeName}"`,
      `"topology.kubernetes.io/region" = "${node.site}"`,
    );
    for (const role of node.target_roles) assertContains(hostDefinition, `"personal-stack/role-${role}" = "true"`);
    for (const capability of node.capabilities) assertContains(hostDefinition, `"personal-stack/capability-${capability}" = "true"`);
    for (const gpu of node.gpus ?? []) {
      assertContains(
        hostDefinition,
        `"personal-stack/gpu-vendor-${gpu.vendor}" = "true"`,
        `"personal-stack/gpu-model-${gpu.model}" = "true"`,
        `"personal-stack/gpu-class-${gpu.class}" = "true"`,
      );
    }
  }
});
