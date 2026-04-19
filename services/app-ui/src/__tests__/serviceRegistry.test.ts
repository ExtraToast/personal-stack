import { describe, expect, it } from 'vitest'
import { getAccessibleServices, SERVICE_REGISTRY } from '../features/apps/data/serviceRegistry'

describe('service registry', () => {
  it('contains all expected permissions', () => {
    const permissions = SERVICE_REGISTRY.map((s) => s.permission)
    expect(permissions).toContain('VAULT')
    expect(permissions).toContain('GRAFANA')
    expect(permissions).toContain('ASSISTANT')
    expect(permissions).toContain('DASHBOARD')
    expect(permissions).toContain('TRAEFIK')
    expect(permissions).toContain('RABBITMQ')
    expect(permissions).toContain('MAIL')
    expect(permissions).toContain('N8N')
    expect(permissions).toContain('STATUS')
    expect(permissions).toContain('JELLYFIN')
    expect(permissions).toContain('JELLYSEERR')
    expect(permissions).toContain('SONARR')
    expect(permissions).toContain('RADARR')
    expect(permissions).toContain('BAZARR')
    expect(permissions).toContain('PROWLARR')
    expect(permissions).toContain('QBITTORRENT')
  })

  it('every entry has required fields', () => {
    for (const entry of SERVICE_REGISTRY) {
      expect(entry.permission).toBeTruthy()
      expect(entry.label).toBeTruthy()
      expect(entry.url).toMatch(/^https?:\/\//u)
      expect(entry.iconUrl).toMatch(/^(https?:\/\/|\/)/u)
      expect(entry.description).toBeTruthy()
    }
  })
})

describe('getAccessibleServices', () => {
  it('returns only services matching given permissions', () => {
    const result = getAccessibleServices(['GRAFANA', 'VAULT'])
    expect(result).toHaveLength(2)
    expect(result.map((s) => s.permission)).toContain('GRAFANA')
    expect(result.map((s) => s.permission)).toContain('VAULT')
  })

  it('returns empty array when no permissions match', () => {
    expect(getAccessibleServices([])).toHaveLength(0)
    expect(getAccessibleServices(['UNKNOWN'])).toHaveLength(0)
  })

  it('returns all services for full permission set', () => {
    const allPerms = SERVICE_REGISTRY.map((s) => s.permission)
    expect(getAccessibleServices(allPerms)).toHaveLength(SERVICE_REGISTRY.length)
  })
})
