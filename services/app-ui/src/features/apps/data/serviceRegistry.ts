export interface ServiceEntry {
  permission: string
  label: string
  url: string
  icon: string
  description: string
}

export const SERVICE_REGISTRY: ServiceEntry[] = [
  {
    permission: 'VAULT',
    label: 'Vault',
    url: 'https://vault.jorisjonkers.dev',
    icon: 'simple-icons:vault',
    description: 'Secrets management',
  },
  {
    permission: 'MAIL',
    label: 'Mail',
    url: 'https://mail.jorisjonkers.dev',
    icon: 'simple-icons:maildotru',
    description: 'Email service',
  },
  {
    permission: 'N8N',
    label: 'n8n',
    url: 'https://n8n.jorisjonkers.dev',
    icon: 'simple-icons:n8n',
    description: 'Workflow automation',
  },
  {
    permission: 'GRAFANA',
    label: 'Grafana',
    url: 'https://grafana.jorisjonkers.dev',
    icon: 'simple-icons:grafana',
    description: 'Observability dashboard',
  },
  {
    permission: 'ASSISTANT',
    label: 'Assistant',
    url: 'https://assistant.jorisjonkers.dev',
    icon: 'mdi:robot-outline',
    description: 'AI assistant',
  },
  {
    permission: 'TRAEFIK_DASHBOARD',
    label: 'Traefik',
    url: 'https://traefik.jorisjonkers.dev',
    icon: 'simple-icons:traefikproxy',
    description: 'Reverse proxy dashboard',
  },
  {
    permission: 'STATUS',
    label: 'Status',
    url: 'https://status.jorisjonkers.dev',
    icon: 'simple-icons:uptimekuma',
    description: 'Service status monitor',
  },
]

export function getAccessibleServices(permissions: string[]): ServiceEntry[] {
  const permSet = new Set(permissions)
  return SERVICE_REGISTRY.filter((s) => permSet.has(s.permission))
}
