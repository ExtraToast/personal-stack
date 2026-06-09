#!/usr/bin/env node

import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'

const inputPath = process.argv[2] ?? 'platform/inventory/fleet.yaml'

const { loadConfig } = await loadToolkit()
const fleet = loadConfig(inputPath)
const deployConfig = toDeployConfig(fleet)

process.stdout.write(`${JSON.stringify(deployConfig, null, 2)}\n`)

async function loadToolkit() {
  try {
    return await import('@extratoast/deploy-config-schema')
  } catch (error) {
    const localCheckout = process.env.DEPLOY_CONFIG_SCHEMA_LOCAL
    if (!localCheckout) {
      throw error
    }
    return import(pathToFileURL(resolve(localCheckout, 'src/index.js')).href)
  }
}

function toDeployConfig(fleet) {
  return {
    version: fleet.version,
    cluster: fleet.cluster,
    sites: fleet.sites,
    nodes: fleet.nodes,
    service_intent: fleet.service_intent,
    placement_intent: placementIntent(fleet),
    exposure_intent: fleet.exposure_intent,
    access_intent: withAccessDefaults(fleet.access_intent),
    ingress_intent: {
      defaults: {
        namespace: 'edge-system',
        public_ingress_class: 'traefik-public',
        lan_ingress_class: 'traefik-lan',
        entrypoint: 'websecure',
        tls: true,
        public_dns_target: `ingress.${fleet.cluster.public_domain}`,
        sso_middleware: 'forward-auth',
      },
      kubernetes_backends: fleet.ingress_intent?.kubernetes_backends ?? {},
      route_rules: routeRules(fleet),
      wan_origin_overrides: fleet.ingress_intent?.wan_origin_overrides ?? {},
    },
    monitoring_intent: {
      kubernetes_backends: fleet.monitoring_intent?.kubernetes_backends ?? {},
    },
    image_metadata: {
      workloads: {},
    },
    adapter_output_intent: {
      adapters: ['traefik-public', 'traefik-lan', 'gatus', 'edge-catalog', 'edge-route-catalog'],
      output_paths: {},
      namespaces: {
        gatus: 'observability',
        'edge-catalog': 'edge-system',
        'edge-route-catalog': 'edge-system',
      },
      configmap_names: {
        gatus: 'gatus-endpoints',
        'edge-catalog': 'platform-edge-catalog',
        'edge-route-catalog': 'platform-edge-route-catalog',
      },
    },
  }
}

function placementIntent(fleet) {
  const intent = fleet.placement_intent ?? {}
  const services = declaredServices(fleet)
  const siteAffinity = {}
  for (const service of intent.frankfurt_only ?? []) {
    if (services.has(service)) {
      siteAffinity[service] = 'frankfurt'
    }
  }
  for (const service of intent.enschede_only ?? []) {
    if (services.has(service)) {
      siteAffinity[service] = 'enschede'
    }
  }

  return {
    site_affinity: sortObject(siteAffinity),
    node_affinity: {},
    gpu_preferences: sortObject(
      Object.fromEntries(Object.entries(intent.gpu_specific ?? {}).filter(([service]) => services.has(service))),
    ),
  }
}

function declaredServices(fleet) {
  const services = new Set()
  for (const group of Object.values(fleet.service_intent?.kubernetes ?? {})) {
    for (const service of group) {
      services.add(service)
    }
  }
  for (const group of Object.values(fleet.service_intent?.host_native ?? {})) {
    for (const service of group) {
      services.add(service)
    }
  }
  return services
}

function withAccessDefaults(accessIntent = {}) {
  return {
    sso_protected: accessIntent.sso_protected ?? [],
    host_labels: accessIntent.host_labels ?? {},
    root_redirect: accessIntent.root_redirect ?? {},
  }
}

function routeRules(fleet) {
  const edgeServices = new Map(
    sortedEntries(edgeCatalogServices(fleet))
      .filter(([, service]) => service.host)
      .map(([serviceName, service]) => [serviceName, service]),
  )
  const specialCasedServices = new Set([
    'app-ui',
    'auth-api',
    'auth-ui',
    'assistant-api',
    'assistant-ui',
    'knowledge-api',
  ])
  const routes = []

  addIfPresent(routes, edgeServices, 'app-ui')

  if (edgeServices.has('auth-api')) {
    routes.push(route(edgeServices.get('auth-api'), { name: 'auth-api', path_prefixes: ['/api/'] }))
    routes.push(route(edgeServices.get('auth-api'), { name: 'auth-api-well-known', path_prefixes: ['/.well-known/'] }))
  }
  if (edgeServices.has('auth-ui')) {
    routes.push(route(edgeServices.get('auth-ui'), { excluded_path_prefixes: ['/api/', '/.well-known/'] }))
  }

  if (edgeServices.has('assistant-api')) {
    routes.push(
      route(edgeServices.get('assistant-api'), {
        path_prefixes: ['/api/'],
        excluded_exact_paths: ['/api/actuator/health', '/api/v1/health'],
        excluded_path_prefixes: ['/api/actuator/health/'],
      }),
    )
    routes.push(
      route(edgeServices.get('assistant-api'), {
        name: 'assistant-api-health',
        access: 'direct',
        exact_paths: ['/api/actuator/health', '/api/v1/health'],
        path_prefixes: ['/api/actuator/health/'],
      }),
    )
  }
  if (edgeServices.has('assistant-ui')) {
    routes.push(route(edgeServices.get('assistant-ui'), { excluded_path_prefixes: ['/api/'] }))
  }

  if (edgeServices.has('knowledge-api')) {
    routes.push(
      route(edgeServices.get('knowledge-api'), {
        name: 'knowledge-api-mcp',
        access: 'direct',
        exact_paths: ['/mcp', '/install.sh'],
        path_prefixes: ['/mcp/'],
      }),
    )
    routes.push(
      route(edgeServices.get('knowledge-api'), {
        excluded_exact_paths: ['/mcp', '/install.sh'],
        excluded_path_prefixes: ['/mcp/'],
      }),
    )
  }

  for (const [serviceName] of edgeServices) {
    if (!specialCasedServices.has(serviceName)) {
      addIfPresent(routes, edgeServices, serviceName)
    }
  }

  return routes.sort((left, right) => left.name.localeCompare(right.name))
}

function edgeCatalogServices(fleet) {
  const exposureByService = {}
  for (const [exposure, services] of Object.entries(fleet.exposure_intent)) {
    for (const service of services) {
      exposureByService[service] = exposure
    }
  }

  return Object.fromEntries(
    Object.entries(exposureByService).map(([serviceName, exposure]) => {
      const hostLabel = fleet.access_intent?.host_labels?.[serviceName]
      return [
        serviceName,
        {
          name: serviceName,
          service: serviceName,
          exposure,
          access: accessForService(fleet, serviceName, exposure),
          host: hostLabel ? fqdn(hostLabel, fleet.cluster.public_domain) : undefined,
        },
      ]
    }),
  )
}

function accessForService(fleet, serviceName, exposure) {
  if ((fleet.access_intent?.sso_protected ?? []).includes(serviceName)) {
    return 'sso_protected'
  }
  if (exposure === 'internal_only') {
    return 'cluster_internal'
  }
  return 'direct'
}

function addIfPresent(routes, services, serviceName) {
  if (services.has(serviceName)) {
    routes.push(route(services.get(serviceName)))
  }
}

function route(service, overrides = {}) {
  return omitUndefined({
    name: overrides.name ?? service.name,
    service: service.service,
    access: overrides.access ?? service.access,
    path_prefixes: overrides.path_prefixes,
    exact_paths: overrides.exact_paths,
    excluded_path_prefixes: overrides.excluded_path_prefixes,
    excluded_exact_paths: overrides.excluded_exact_paths,
  })
}

function fqdn(hostLabel, domain) {
  return hostLabel === 'root' ? domain : `${hostLabel}.${domain}`
}

function sortedEntries(object = {}) {
  return Object.entries(object).sort(([left], [right]) => left.localeCompare(right))
}

function sortObject(object = {}) {
  return Object.fromEntries(sortedEntries(object))
}

function omitUndefined(object) {
  return Object.fromEntries(Object.entries(object).filter(([, value]) => value !== undefined))
}
