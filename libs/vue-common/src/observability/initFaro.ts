// Grafana Faro Web SDK is loaded dynamically so consumers that don't
// call `initFaro` (tests, local dev without VITE_FARO_URL) pay no
// bundle cost. The lazy import + once-only guard mean repeated calls
// are safe.

export interface InitFaroOptions {
  appName: string
  appVersion?: string
  environment?: string
  // URL of the Alloy OTLP-HTTP receiver. When empty, Faro is not
  // initialised; the UI builds and runs unchanged. Production builds
  // set this via VITE_FARO_URL at build time.
  otlpUrl: string | undefined
}

let initialised = false

export async function initFaro(options: InitFaroOptions): Promise<void> {
  if (initialised) return
  if (typeof window === 'undefined') return
  if (!options.otlpUrl) return

  try {
    const [sdk, tracing] = await Promise.all([import('@grafana/faro-web-sdk'), import('@grafana/faro-web-tracing')])
    const { initializeFaro, getWebInstrumentations } = sdk
    const { TracingInstrumentation } = tracing

    initializeFaro({
      url: options.otlpUrl,
      app: {
        name: options.appName,
        version: options.appVersion ?? 'latest',
        environment: options.environment ?? 'production',
      },
      instrumentations: [...getWebInstrumentations(), new TracingInstrumentation()],
      sessionTracking: { enabled: true },
    })
    initialised = true
  } catch (err) {
    // Never let observability tooling crash the SPA bootstrap. A
    // failed Faro init means we lose RUM but the app still loads.

    console.warn('[observability] failed to initialise Faro:', err)
  }
}

// Test-only escape hatch — resets the once-only flag between vitest
// cases so each test can drive a fresh init().
export function resetFaroForTests(): void {
  initialised = false
}
