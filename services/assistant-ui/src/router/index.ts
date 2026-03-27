import type { RouteRecordRaw } from 'vue-router'
import { useAuth } from '@personal-stack/vue-common'
import { createRouter, createWebHistory } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat',
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/features/chat/views/ChatView.vue'),
    meta: { requiresAuth: true },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const { isAuthenticated } = useAuth()

  if (to.meta.requiresAuth && !isAuthenticated.value) {
    // Redirect to auth-ui login (cross-origin in production, same-origin in dev with proxy)
    window.location.href = `${import.meta.env.VITE_AUTH_URL ?? 'http://localhost:5174'}/login?redirect=${encodeURIComponent(window.location.href)}`
    return false
  }

  return true
})
