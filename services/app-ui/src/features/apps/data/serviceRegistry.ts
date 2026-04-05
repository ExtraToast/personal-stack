export interface ServiceEntry {
  permission: string
  label: string
  url: string
  iconUrl: string
  description: string
}

const DEFAULT_STACK_DOMAIN = 'jorisjonkers.dev'

function resolveStackDomain(hostname?: string): string {
  if (!hostname || hostname === 'localhost' || hostname === '127.0.0.1') {
    return DEFAULT_STACK_DOMAIN
  }

  const parts = hostname.split('.')
  if (parts.length < 2) {
    return DEFAULT_STACK_DOMAIN
  }

  return parts.slice(-2).join('.')
}

function buildServiceUrl(subdomain: string, path = '/'): string {
  const hostname = typeof window !== 'undefined' ? window.location.hostname : undefined
  const domain = resolveStackDomain(hostname)
  return `https://${subdomain}.${domain}${path}`
}

export const SERVICE_REGISTRY: ServiceEntry[] = [
  {
    permission: 'VAULT',
    label: 'Vault',
    url: buildServiceUrl('vault'),
    iconUrl: '/icons/vault.svg',
    description: 'Secrets management',
  },
  {
    permission: 'MAIL',
    label: 'Stalwart',
    url: buildServiceUrl('stalwart'),
    iconUrl: '/icons/stalwart.svg',
    description: 'Mail server',
  },
  {
    permission: 'N8N',
    label: 'n8n',
    url: buildServiceUrl('n8n'),
    iconUrl: '/icons/n8n.ico',
    description: 'Workflow automation',
  },
  {
    permission: 'GRAFANA',
    label: 'Grafana',
    url: buildServiceUrl('grafana'),
    iconUrl: '/icons/grafana.png',
    description: 'Observability dashboard',
  },
  {
    permission: 'ASSISTANT',
    label: 'Assistant',
    url: buildServiceUrl('assistant'),
    iconUrl: '/icons/assistant.svg',
    description: 'AI assistant',
  },
  {
    permission: 'NOMAD',
    label: 'Nomad',
    url: buildServiceUrl('nomad'),
    iconUrl: '/icons/nomad.svg',
    description: 'Cluster orchestration',
  },
  {
    permission: 'RABBITMQ',
    label: 'RabbitMQ',
    url: buildServiceUrl('rabbitmq'),
    iconUrl: '/icons/rabbitmq.svg',
    description: 'Message broker',
  },
  {
    permission: 'TRAEFIK_DASHBOARD',
    label: 'Traefik',
    url: buildServiceUrl('traefik'),
    iconUrl: '/icons/traefik.svg',
    description: 'Reverse proxy dashboard',
  },
  {
    permission: 'STATUS',
    label: 'Status',
    url: buildServiceUrl('status'),
    iconUrl: '/icons/status.svg',
    description: 'Service status monitor',
  },
]

export function getAccessibleServices(permissions: string[]): ServiceEntry[] {
  const permSet = new Set(permissions)
  return SERVICE_REGISTRY.filter((s) => permSet.has(s.permission))
}
