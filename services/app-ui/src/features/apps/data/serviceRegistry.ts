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
    permission: 'DASHBOARD',
    label: 'Cluster Dashboard',
    url: buildServiceUrl('dashboard'),
    iconUrl: '/icons/dashboard.svg',
    description: 'Headlamp kubernetes dashboard',
  },
  {
    permission: 'TRAEFIK',
    label: 'Traefik',
    url: buildServiceUrl('traefik'),
    iconUrl: '/icons/traefik.png',
    description: 'Edge router dashboard',
  },
  {
    permission: 'RABBITMQ',
    label: 'RabbitMQ',
    url: buildServiceUrl('rabbitmq'),
    iconUrl: '/icons/rabbitmq.svg',
    description: 'Message broker',
  },
  {
    permission: 'STATUS',
    label: 'Status',
    url: buildServiceUrl('status'),
    iconUrl: '/icons/status.svg',
    description: 'Service status monitor',
  },
  {
    permission: 'JELLYFIN',
    label: 'Jellyfin',
    url: buildServiceUrl('jellyfin'),
    iconUrl: '/icons/jellyfin.svg',
    description: 'Media server',
  },
  {
    permission: 'JELLYSEERR',
    label: 'Jellyseerr',
    url: buildServiceUrl('jellyseerr'),
    iconUrl: '/icons/jellyseerr.svg',
    description: 'Media requests',
  },
  {
    permission: 'SONARR',
    label: 'Sonarr',
    url: buildServiceUrl('sonarr'),
    iconUrl: '/icons/sonarr.png',
    description: 'TV series manager',
  },
  {
    permission: 'RADARR',
    label: 'Radarr',
    url: buildServiceUrl('radarr'),
    iconUrl: '/icons/radarr.png',
    description: 'Movie manager',
  },
  {
    permission: 'BAZARR',
    label: 'Bazarr',
    url: buildServiceUrl('bazarr'),
    iconUrl: '/icons/bazarr.png',
    description: 'Subtitle manager',
  },
  {
    permission: 'PROWLARR',
    label: 'Prowlarr',
    url: buildServiceUrl('prowlarr'),
    iconUrl: '/icons/prowlarr.png',
    description: 'Indexer manager',
  },
  {
    permission: 'QBITTORRENT',
    label: 'qBittorrent',
    url: buildServiceUrl('qbittorrent'),
    iconUrl: '/icons/qbittorrent.png',
    description: 'Torrent client',
  },
]

export function getAccessibleServices(permissions: string[]): ServiceEntry[] {
  const permSet = new Set(permissions)
  return SERVICE_REGISTRY.filter((s) => permSet.has(s.permission))
}
