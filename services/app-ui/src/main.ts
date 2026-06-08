import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { initFaro } from '@/lib/vueWebCommons'
import App from './App.vue'
import { useAuthStore } from './features/auth'
import { i18n } from './i18n'
import { router } from './router'
import './index.css'

async function bootstrap(): Promise<void> {
  // Real-user monitoring. No-op in dev (VITE_FARO_URL unset); in prod
  // ships browser navigation + fetch + error spans to Alloy → Tempo.
  void initFaro({
    appName: 'app-ui',
    environment: import.meta.env.MODE,
    otlpUrl: import.meta.env.VITE_FARO_URL,
  })

  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  app.use(router)
  app.use(i18n)
  await router.isReady()
  app.mount('#app')

  // Kick off the cross-subdomain session check in the background. The auth
  // backend can take ~17s to respond on a cold start; blocking app.mount()
  // on it leaves the page blank for that whole window. Mount first, then
  // let auth-aware components reactively update once /me lands.
  void useAuthStore(pinia).initSession()
}

void bootstrap()
