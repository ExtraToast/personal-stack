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
    // Workspace detail (repo-backed or scratch). Reached from the
    // Sessions > Workspace tab cards and the Scratch tab. The view
    // still lives under `features/workspaces/` because it consumes
    // that feature's components + store directly; only the URL is
    // namespaced under `/sessions/` to match the redesigned IA.
    path: '/sessions/workspace/:id',
    name: 'workspace-detail',
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
