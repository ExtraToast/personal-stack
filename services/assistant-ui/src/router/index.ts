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
    redirect: '/sessions',
  },
  {
    path: '/sessions',
    name: 'sessions',
    component: () => import('@/features/sessions/views/SessionsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    // Legacy chat route stays as an alias of /sessions during the
    // migration window so deep links and bookmarks still resolve.
    path: '/chat',
    redirect: '/sessions',
  },
  {
    path: '/workspaces',
    name: 'workspaces',
    component: () => import('@/features/workspaces/views/WorkspacesView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/workspaces/:id',
    name: 'workspace',
    component: () => import('@/features/workspaces/views/WorkspaceView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/projects',
    name: 'projects',
    component: () => import('@/features/projects/views/ProjectsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/projects/:id',
    name: 'project',
    component: () => import('@/features/projects/views/ProjectView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/repositories',
    name: 'repositories',
    component: () => import('@/features/repositories/views/RepositoriesView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/repositories/:id',
    name: 'repository',
    component: () => import('@/features/repositories/views/RepositoryView.vue'),
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
