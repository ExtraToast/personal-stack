const AUTH_BASE_URL: string = import.meta.env.VITE_AUTH_URL ?? 'https://auth.jorisjonkers.dev'

export { AUTH_BASE_URL }

export function getCsrfToken(): string | null {
  if (typeof document === 'undefined') return null
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null
}

export async function authedFetch(url: string, init?: RequestInit): Promise<Response> {
  const method = init?.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = getCsrfToken()
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf)
    }
  }
  const response = await fetch(url, { ...init, headers, credentials: 'include' })
  if (!response.ok) throw new Error(`Request failed: ${response.status}`)
  return response
}
