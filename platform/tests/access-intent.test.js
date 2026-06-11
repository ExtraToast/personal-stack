import test from "node:test";
import assert from "node:assert/strict";
import { loadFleet } from "./_helpers.js";

test("vault is modeled as a public sso protected service", async () => {
  const fleet = await loadFleet();
  assert.ok(fleet.access_intent.sso_protected.includes("vault"));
  assert.equal(fleet.access_intent.host_labels.vault, "vault");
  assert.ok(fleet.exposure_intent.public.includes("vault"));
  assert.ok(!fleet.exposure_intent.internal_only.includes("vault"));
});

test("edge exposed services declare stable host labels", async () => {
  const fleet = await loadFleet();
  assert.equal(fleet.access_intent.host_labels["app-ui"], "root");
  assert.equal(fleet.access_intent.host_labels["auth-ui"], "auth");
  assert.equal(fleet.access_intent.host_labels["assistant-ui"], "assistant");
  assert.equal(fleet.access_intent.host_labels.stalwart, "stalwart");
  assert.equal(fleet.access_intent.host_labels.gatus, "status");
  assert.equal(fleet.access_intent.host_labels.bazarr, "bazarr");
  assert.equal(fleet.access_intent.host_labels.prowlarr, "prowlarr");
  assert.equal(fleet.access_intent.host_labels.qbittorrent, "qbittorrent");
  assert.equal(fleet.access_intent.host_labels.jellyseerr, "jellyseerr");
});

test("rabbitmq is modeled as a public sso protected service", async () => {
  const fleet = await loadFleet();
  assert.ok(fleet.access_intent.sso_protected.includes("rabbitmq"));
  assert.equal(fleet.access_intent.host_labels.rabbitmq, "rabbitmq");
  assert.ok(fleet.exposure_intent.public.includes("rabbitmq"));
  assert.ok(!fleet.exposure_intent.internal_only.includes("rabbitmq"));
});

test("stalwart admin is modeled as a public sso protected service", async () => {
  const fleet = await loadFleet();
  assert.ok(fleet.access_intent.sso_protected.includes("stalwart"));
  assert.equal(fleet.access_intent.host_labels.stalwart, "stalwart");
  assert.ok(fleet.exposure_intent.public.includes("stalwart"));
  assert.ok(!fleet.exposure_intent.internal_only.includes("stalwart"));
});

test("media tools are public on both edges with external sso enforcement", async () => {
  const fleet = await loadFleet();
  for (const service of ["bazarr", "prowlarr", "qbittorrent", "jellyseerr"]) {
    assert.ok(fleet.exposure_intent.public_and_lan.includes(service));
    assert.ok(!fleet.exposure_intent.internal_only.includes(service));
  }
  for (const service of ["bazarr", "prowlarr", "qbittorrent"]) assert.ok(fleet.access_intent.sso_protected.includes(service));
  assert.ok(!fleet.access_intent.sso_protected.includes("jellyseerr"));
});
