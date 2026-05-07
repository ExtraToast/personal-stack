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
