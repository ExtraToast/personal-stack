import type { RouteRecordRaw } from 'vue-router'
import { useAuth } from '@private-stack/vue-common'
import { createRouter, createWebHistory } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiredRoles?: string[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/features/auth/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/features/auth/views/RegisterView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/totp-setup',
    name: 'totp-setup',
    component: () => import('@/features/auth/views/TotpSetupView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/',
    name: 'dashboard',
    component: () => import('@/features/auth/views/LoginView.vue'), // placeholder until dashboard exists
    meta: { requiresAuth: true },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const { isAuthenticated } = useAuth()

  if (to.meta.requiresAuth === false) {
    // Public route — redirect authenticated users away from login/register
    if (isAuthenticated.value && (to.name === 'login' || to.name === 'register')) {
      return { name: 'dashboard' }
    }
    return true
  }

  // Protected route — redirect unauthenticated users to login
  if (!isAuthenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})
