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
    iconUrl: buildServiceUrl('vault', '/ui/favicon.ico'),
    description: 'Secrets management',
  },
  {
    permission: 'MAIL',
    label: 'Stalwart',
    url: buildServiceUrl('mail'),
    iconUrl: buildServiceUrl('mail', '/favicon.ico'),
    description: 'Mail server',
  },
  {
    permission: 'N8N',
    label: 'n8n',
    url: buildServiceUrl('n8n'),
    iconUrl: buildServiceUrl('n8n', '/favicon.ico'),
    description: 'Workflow automation',
  },
  {
    permission: 'GRAFANA',
    label: 'Grafana',
    url: buildServiceUrl('grafana'),
    iconUrl: buildServiceUrl('grafana', '/public/img/fav32.png'),
    description: 'Observability dashboard',
  },
  {
    permission: 'ASSISTANT',
    label: 'Assistant',
    url: buildServiceUrl('assistant'),
    iconUrl: buildServiceUrl('assistant', '/favicon.svg'),
    description: 'AI assistant',
  },
  {
    permission: 'TRAEFIK_DASHBOARD',
    label: 'Traefik',
    url: buildServiceUrl('traefik'),
    iconUrl: buildServiceUrl('traefik', '/favicon.ico'),
    description: 'Reverse proxy dashboard',
  },
  {
    permission: 'STATUS',
    label: 'Status',
    url: buildServiceUrl('status'),
    iconUrl: buildServiceUrl('status', '/icon.svg'),
    description: 'Service status monitor',
  },
]

export function getAccessibleServices(permissions: string[]): ServiceEntry[] {
  const permSet = new Set(permissions)
  return SERVICE_REGISTRY.filter((s) => permSet.has(s.permission))
}
