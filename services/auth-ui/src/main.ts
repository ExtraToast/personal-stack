import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import './index.css'

async function bootstrap(): Promise<void> {
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
