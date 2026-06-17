import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdtemp, readdir, readFile, stat, writeFile, chmod } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'

export const testsDir = path.dirname(fileURLToPath(import.meta.url))
export const repoRoot = path.resolve(testsDir, '../..')

export function repoPath(...segments) {
  return path.join(repoRoot, ...segments)
}

export async function readRepoText(...segments) {
  return readFile(repoPath(...segments), 'utf8')
}

export async function readYamlRepo(...segments) {
  return YAML.parse(await readRepoText(...segments))
}

export async function loadFleet() {
  const fleet = await readYamlRepo('platform/inventory/fleet.yaml')
  validateFleet(fleet)
  return fleet
}

export function validateFleet(fleet) {
  const k8s = fleet.cluster.kubernetes
  const bootstrap = fleet.nodes[k8s.bootstrap_control_plane]
  assert.ok(bootstrap, `bootstrap control plane ${k8s.bootstrap_control_plane} is not defined as a node`)
  assert.ok(
    bootstrap.target_roles.includes('k3s-control-plane'),
    `bootstrap control plane ${k8s.bootstrap_control_plane} must target the k3s-control-plane role`,
  )
  assert.ok(k8s.api_server_endpoint.startsWith('https://'), 'cluster kubernetes api_server_endpoint must use https')
  assert.ok(
    k8s.control_plane_token_file.startsWith('/'),
    'cluster kubernetes control_plane_token_file must be an absolute path',
  )
  assert.ok(
    k8s.worker_join_token_file.startsWith('/'),
    'cluster kubernetes worker_join_token_file must be an absolute path',
  )

  for (const [nodeName, node] of Object.entries(fleet.nodes)) {
    assert.ok(node.site in fleet.sites, `node ${nodeName} references unknown site ${node.site}`)
    if (node.status === 'active') {
      assert.ok(node.ssh, `active node ${nodeName} must define ssh connection details`)
    }
  }

  const k = fleet.service_intent.kubernetes
  const knownKubernetesServices = new Set([...k.public_apps, ...k.internal_platform, ...k.home_media])
  const knownServices = new Set(knownKubernetesServices)
  for (const services of Object.values(fleet.service_intent.host_native ?? {})) {
    for (const service of services) knownServices.add(service)
  }
  const externallyExposedServices = new Set([
    ...(fleet.exposure_intent.public ?? []),
    ...(fleet.exposure_intent.public_and_lan ?? []),
    ...(fleet.exposure_intent.lan_only ?? []),
  ])
  const externallyExposedKubernetesServices = new Set(
    [...externallyExposedServices].filter((service) => knownKubernetesServices.has(service)),
  )

  for (const serviceName of fleet.access_intent?.sso_protected ?? []) {
    assert.ok(knownServices.has(serviceName), `sso protected service ${serviceName} is not defined in service intent`)
    assert.ok(
      externallyExposedServices.has(serviceName),
      `sso protected service ${serviceName} must be externally exposed`,
    )
  }

  for (const [serviceName, hostLabel] of Object.entries(fleet.access_intent?.host_labels ?? {})) {
    assert.ok(knownServices.has(serviceName), `host label for service ${serviceName} references unknown service intent`)
    assert.ok(String(hostLabel).trim().length > 0, `host label for service ${serviceName} must not be blank`)
  }

  for (const serviceName of externallyExposedServices) {
    assert.ok(
      serviceName in (fleet.access_intent?.host_labels ?? {}),
      `externally exposed service ${serviceName} must declare a host label`,
    )
  }

  for (const [serviceName, backend] of Object.entries(fleet.ingress_intent?.kubernetes_backends ?? {})) {
    assert.ok(
      knownKubernetesServices.has(serviceName),
      `ingress backend for service ${serviceName} references unknown kubernetes service intent`,
    )
    assert.ok(
      externallyExposedKubernetesServices.has(serviceName),
      `ingress backend for service ${serviceName} must target an externally exposed kubernetes service`,
    )
    validateBackend('ingress', serviceName, backend, true, fleet)
  }
  for (const serviceName of externallyExposedKubernetesServices) {
    assert.ok(
      serviceName in (fleet.ingress_intent?.kubernetes_backends ?? {}),
      `externally exposed kubernetes service ${serviceName} must declare an ingress backend`,
    )
  }

  for (const [serviceName, origin] of Object.entries(fleet.ingress_intent?.wan_origin_overrides ?? {})) {
    assert.ok(
      externallyExposedKubernetesServices.has(serviceName),
      `wan origin override for service ${serviceName} must target an externally exposed kubernetes service`,
    )
    assert.ok(
      origin === 'home_direct' || origin === 'edge_direct',
      `wan origin override for service ${serviceName} must be 'home_direct' or 'edge_direct' (got '${origin}')`,
    )
  }
  const overrideModes = new Set(Object.values(fleet.ingress_intent?.wan_origin_overrides ?? {}))
  if (overrideModes.has('home_direct')) {
    assert.ok(
      Object.values(fleet.sites).some(
        (site) => site.purpose === 'home_lan_and_media_site' && site.networking?.wan_public_ip,
      ),
      "wan_origin_overrides 'home_direct' needs the home site to set networking.wan_public_ip",
    )
  }
  if (overrideModes.has('edge_direct')) {
    assert.ok(
      Object.values(fleet.sites).some(
        (site) => site.purpose === 'primary_cluster_site' && site.networking?.wan_public_ip,
      ),
      "wan_origin_overrides 'edge_direct' needs the primary cluster site to set networking.wan_public_ip",
    )
  }

  for (const [serviceName, backend] of Object.entries(fleet.monitoring_intent?.kubernetes_backends ?? {})) {
    assert.ok(
      knownKubernetesServices.has(serviceName),
      `monitoring backend for service ${serviceName} references unknown kubernetes service intent`,
    )
    assert.ok(
      !externallyExposedKubernetesServices.has(serviceName),
      `monitoring backend for service ${serviceName} duplicates an ingress backend; probe it via ingress_intent instead`,
    )
    validateBackend('monitoring', serviceName, backend, false, fleet)
  }

  const lanExposedServices = [
    ...(fleet.exposure_intent.public_and_lan ?? []),
    ...(fleet.exposure_intent.lan_only ?? []),
  ]
  if (lanExposedServices.length > 0) {
    assert.ok(
      Object.values(fleet.sites).some((site) => site.networking?.lan_ingress_ip),
      'lan exposed services require at least one site lan ingress ip',
    )
    assert.ok(
      Object.values(fleet.nodes).some((node) => node.status === 'active' && node.capabilities.includes('lan-ingress')),
      'lan exposed services require at least one active lan ingress node',
    )
  }
}

function validateBackend(kind, serviceName, backend, ingress, fleet) {
  assert.ok(
    String(backend.namespace ?? '').trim().length > 0,
    `${kind} backend namespace for service ${serviceName} must not be blank`,
  )
  assert.ok(
    String(backend.service ?? '').trim().length > 0,
    `${kind} backend service name for service ${serviceName} must not be blank`,
  )
  assert.ok(backend.port > 0, `${kind} backend port for service ${serviceName} must be positive`)
  if (backend.health) {
    const health = { type: 'http', path: '/', ...backend.health }
    assert.ok(
      ['http', 'tcp'].includes(health.type),
      `${kind === 'monitoring' ? 'monitoring ' : ''}health type for service ${serviceName} must be http or tcp`,
    )
    if (health.type === 'tcp') {
      assert.equal(
        health.path,
        '/',
        `tcp ${kind === 'monitoring' ? 'monitoring ' : ''}health for service ${serviceName} must not set a path`,
      )
      assert.equal(
        health.expected_status,
        undefined,
        `tcp ${kind === 'monitoring' ? 'monitoring ' : ''}health for service ${serviceName} must not set expected_status`,
      )
    }
    assert.ok(
      health.path.startsWith('/'),
      `${kind === 'monitoring' ? 'monitoring ' : ''}health path for service ${serviceName} must start with /`,
    )
    if (health.port !== undefined)
      assert.ok(
        health.port > 0,
        `${kind === 'monitoring' ? 'monitoring ' : ''}health port for service ${serviceName} must be positive`,
      )
    if (ingress && health.probe_strategy !== undefined) {
      assert.ok(
        ['internal', 'external', 'both'].includes(health.probe_strategy),
        `health probe_strategy for service ${serviceName} must be internal, external, or both`,
      )
      if (['external', 'both'].includes(health.probe_strategy)) {
        assert.ok(
          serviceName in (fleet.access_intent?.host_labels ?? {}),
          `health probe_strategy ${health.probe_strategy} for service ${serviceName} requires a host label`,
        )
      }
    }
    if (!ingress) {
      assert.ok(
        health.probe_strategy === undefined || health.probe_strategy === 'internal',
        `monitoring health probe_strategy for service ${serviceName} must be internal (monitoring targets have no external host)`,
      )
    }
  }
  for (const probe of backend.extra_probes ?? []) {
    assert.ok(String(probe.name ?? '').trim().length > 0, `extra probe for service ${serviceName} must define a name`)
    assert.ok(probe.port > 0, `extra probe ${probe.name} for service ${serviceName} must use a positive port`)
    const type = probe.type ?? 'tcp'
    const probePath = probe.path ?? '/'
    assert.ok(
      ['http', 'tcp'].includes(type),
      `extra probe ${probe.name} for service ${serviceName} must be http or tcp`,
    )
    if (type === 'tcp') {
      assert.equal(probePath, '/', `tcp extra probe ${probe.name} for service ${serviceName} must not set a path`)
      assert.equal(
        probe.expected_status,
        undefined,
        `tcp extra probe ${probe.name} for service ${serviceName} must not set expected_status`,
      )
    }
    assert.ok(probePath.startsWith('/'), `extra probe ${probe.name} for service ${serviceName} path must start with /`)
  }
}

export function parseFleetText(text) {
  const fleet = YAML.parse(text)
  validateFleet(fleet)
  return fleet
}

export function assertContains(text, ...needles) {
  for (const needle of needles) assert.ok(text.includes(needle), `expected text to contain ${needle}`)
}

export function assertNotContains(text, ...needles) {
  for (const needle of needles) assert.ok(!text.includes(needle), `expected text not to contain ${needle}`)
}

export function assertSameMembers(actual, expected, message) {
  assert.deepEqual([...actual].sort(), [...expected].sort(), message)
}

export async function filesUnder(root) {
  if (!existsSync(root)) return []
  const out = []
  async function walk(dir) {
    for (const entry of await readdir(dir)) {
      const full = path.join(dir, entry)
      const info = await stat(full)
      if (info.isDirectory()) await walk(full)
      else if (info.isFile()) out.push(full)
    }
  }
  await walk(root)
  return out
}

export function relativePath(root, file) {
  return path.relative(root, file).replaceAll(path.sep, '/')
}

export async function sha256(file) {
  return createHash('sha256')
    .update(await readFile(file))
    .digest('hex')
}

export async function tempDir(prefix = 'platform-tests-') {
  return mkdtemp(path.join(tmpdir(), prefix))
}

export async function writeExecutable(file, contents) {
  await writeFile(file, contents.trimStart().replace(/\n?$/, '\n'), 'utf8')
  await chmod(file, 0o755)
  return file
}

export async function runProcess(command, args = [], { env = {}, input, cwd = repoRoot } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      env: { ...process.env, ...env },
      stdio: ['pipe', 'pipe', 'pipe'],
    })
    let stdout = ''
    let stderr = ''
    child.stdout.setEncoding('utf8')
    child.stderr.setEncoding('utf8')
    child.stdout.on('data', (chunk) => {
      stdout += chunk
    })
    child.stderr.on('data', (chunk) => {
      stderr += chunk
    })
    child.on('error', reject)
    child.on('close', (exitCode) => resolve({ exitCode, stdout, stderr }))
    // A spawned process may legitimately close its stdin before we finish
    // writing the payload — e.g. a disabled/silent hook that exits without
    // reading input. The unconsumed write then races the closed pipe and
    // surfaces as an EPIPE on the stdin stream which, unhandled, rejects the
    // whole call with "Error: write EPIPE" (the source of the flaky platform
    // test). Swallow only EPIPE and let the `close` handler report the real
    // exit code; surface any other stdin error.
    child.stdin.on('error', (err) => {
      if (err && err.code === 'EPIPE') return
      reject(err)
    })
    try {
      if (input !== undefined) child.stdin.end(input)
      else child.stdin.end()
    } catch (err) {
      if (!err || err.code !== 'EPIPE') reject(err)
    }
  })
}

export function yamlDocs(text) {
  return YAML.parseAllDocuments(text)
    .map((doc) => doc.toJSON())
    .filter(Boolean)
}

export function getPath(object, keys) {
  let current = object
  for (const key of keys) current = current?.[key]
  return current
}
