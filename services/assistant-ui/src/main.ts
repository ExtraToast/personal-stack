import { initFaro, useAuth } from '@personal-stack/vue-common'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './index.css'

const AUTH_BASE_URL = import.meta.env.VITE_AUTH_URL ?? 'http://localhost:5174'

async function bootstrap(): Promise<void> {
  // Real-user monitoring. See app-ui/src/main.ts for the rationale.
  void initFaro({
    appName: 'assistant-ui',
    environment: import.meta.env.MODE,
    otlpUrl: import.meta.env.VITE_FARO_URL,
  })

  const app = createApp(App)
  app.use(createPinia())

  // Resolve the existing auth session before protected-route guards run.
  await useAuth().fetchUser(`${AUTH_BASE_URL}/api/v1`)

  app.use(router)
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
