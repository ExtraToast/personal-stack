import { beforeEach, describe, expect, it, vi } from 'vitest'

const initializeFaroMock = vi.fn()
const getWebInstrumentationsMock = vi.fn(() => [{ name: 'web' }])
const tracingInstrumentationCtor = vi.fn()

vi.mock('@grafana/faro-web-sdk', () => ({
  initializeFaro: initializeFaroMock,
  getWebInstrumentations: getWebInstrumentationsMock,
}))

vi.mock('@grafana/faro-web-tracing', () => ({
  TracingInstrumentation: tracingInstrumentationCtor,
}))

interface FaroConfig {
  url: string
  app: { name: string; version: string; environment: string }
  instrumentations: unknown[]
}

function isFaroConfig(value: unknown): value is FaroConfig {
  return typeof value === 'object' && value !== null && 'url' in value && 'app' in value
}

function firstCallConfig(): FaroConfig {
  const call = initializeFaroMock.mock.calls[0]
  if (!call) throw new Error('initializeFaro was not called')
  const config = call[0]
  if (!isFaroConfig(config)) throw new Error('initializeFaro was called with an unexpected shape')
  return config
}

describe('initFaro', () => {
  beforeEach(async () => {
    initializeFaroMock.mockReset()
    getWebInstrumentationsMock.mockClear()
    tracingInstrumentationCtor.mockClear()
    const { resetFaroForTests } = await import('../observability/initFaro')
    resetFaroForTests()
  })

  it('does nothing when otlpUrl is undefined', async () => {
    const { initFaro } = await import('../observability/initFaro')
    await initFaro({ appName: 'app-ui', otlpUrl: undefined })
    expect(initializeFaroMock).not.toHaveBeenCalled()
  })

  it('does nothing when otlpUrl is an empty string', async () => {
    const { initFaro } = await import('../observability/initFaro')
    await initFaro({ appName: 'app-ui', otlpUrl: '' })
    expect(initializeFaroMock).not.toHaveBeenCalled()
  })

  it('initialises Faro with the expected app metadata and instrumentations', async () => {
    const { initFaro } = await import('../observability/initFaro')
    await initFaro({
      appName: 'app-ui',
      appVersion: '1.2.3',
      environment: 'production',
      otlpUrl: 'https://faro.example.dev/v1/traces',
    })

    expect(initializeFaroMock).toHaveBeenCalledTimes(1)
    const config = firstCallConfig()
    expect(config.url).toBe('https://faro.example.dev/v1/traces')
    expect(config.app).toEqual({ name: 'app-ui', version: '1.2.3', environment: 'production' })
    expect(Array.isArray(config.instrumentations)).toBe(true)
    expect(tracingInstrumentationCtor).toHaveBeenCalledTimes(1)
  })

  it('only initialises once even with repeated calls', async () => {
    const { initFaro } = await import('../observability/initFaro')
    await initFaro({ appName: 'app-ui', otlpUrl: 'https://faro.example.dev/v1/traces' })
    await initFaro({ appName: 'app-ui', otlpUrl: 'https://faro.example.dev/v1/traces' })
    expect(initializeFaroMock).toHaveBeenCalledTimes(1)
  })

  it('defaults version to "latest" and environment to "production"', async () => {
    const { initFaro } = await import('../observability/initFaro')
    await initFaro({ appName: 'auth-ui', otlpUrl: 'https://faro.example.dev/v1/traces' })
    const config = firstCallConfig()
    expect(config.app).toEqual({ name: 'auth-ui', version: 'latest', environment: 'production' })
  })
})
