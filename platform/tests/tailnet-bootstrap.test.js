import test from 'node:test'
import assert from 'node:assert/strict'
import { loadFleet, readRepoText, assertContains } from './_helpers.js'

test('platform inventory no longer models headscale as a planned service', async () => {
  const fleet = await loadFleet()
  const hostNativeServices = Object.values(fleet.service_intent.host_native).flat()
  const placementServices = [...fleet.placement_intent.frankfurt_only, ...fleet.placement_intent.enschede_only]
  const exposedServices = [
    ...fleet.exposure_intent.public,
    ...fleet.exposure_intent.public_and_lan,
    ...fleet.exposure_intent.internal_only,
    ...fleet.exposure_intent.lan_only,
  ]
  assert.ok(!hostNativeServices.includes('headscale'))
  assert.ok(!placementServices.includes('headscale'))
  assert.ok(!exposedServices.includes('headscale'))
  assert.ok(!('headscale' in fleet.access_intent.host_labels))
})

test('bootstrap docs describe Tailscale admin console auth key flow', async () => {
  const platformReadme = await readRepoText('platform/README.md')
  const bootstrapReadme = await readRepoText('platform/cluster/bootstrap/README.md')
  const tailnetPlaybook = await readRepoText('platform/cluster/bootstrap/tailscale-tailnet-playbook.md')
  assertContains(platformReadme, 'hosted `Tailscale` admin console', 'bootstrap-tailnet.sh')
  assertContains(bootstrapReadme, 'tailscale-tailnet-playbook.md')
  assertContains(
    tailnetPlaybook,
    'one-off auth key',
    'Tailscale admin console',
    'bootstrap-tailnet.sh <node-name>',
    'MagicDNS',
  )
})

test('tailnet bootstrap helper expects an auth key and runs tailscale up remotely', async () => {
  const helperScript = await readRepoText('platform/scripts/bootstrap/bootstrap-tailnet.sh')
  assertContains(
    helperScript,
    'TS_AUTH_KEY',
    'PLATFORM_SSH_IDENTITY_FILE',
    'BOOTSTRAP_SSH_HOST',
    'require_platform_ssh_identity_file_if_set',
    'tailscale up',
    '--auth-key=',
    '--hostname=',
    'tailscale status',
  )
})
