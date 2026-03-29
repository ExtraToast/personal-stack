import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { useAuthStore } from './features/auth'
import { i18n } from './i18n'
import { router } from './router'
import './index.css'

async function bootstrap(): Promise<void> {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  // Restore any existing cross-subdomain session before views render.
  await useAuthStore(pinia)
    .fetchSession()
    .catch(() => undefined)

  app.use(router)
  app.use(i18n)
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
