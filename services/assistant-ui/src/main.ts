import { useAuth } from '@personal-stack/vue-common'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './index.css'

const AUTH_BASE_URL = import.meta.env.VITE_AUTH_URL ?? 'http://localhost:5174'

async function bootstrap(): Promise<void> {
  const app = createApp(App)
  app.use(createPinia())

  // Resolve the existing auth session before protected-route guards run.
  await useAuth().fetchUser(`${AUTH_BASE_URL}/api/v1`)

  app.use(router)
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
