import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { initFaro } from '@/lib/vueWebCommons'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import './index.css'

async function bootstrap(): Promise<void> {
  // Real-user monitoring. See app-ui/src/main.ts for the rationale.
  void initFaro({
    appName: 'auth-ui',
    environment: import.meta.env.MODE,
    otlpUrl: import.meta.env.VITE_FARO_URL,
  })

  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  // Hydrate the current session before router guards decide on redirects.
  await useAuthStore(pinia)
    .checkSession()
    .catch(() => undefined)

  app.use(router)
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
