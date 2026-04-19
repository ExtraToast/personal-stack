import type { RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/features/home/views/HomeView.vue'),
  },
  {
    path: '/apps',
    name: 'apps',
    component: () => import('@/features/apps/views/AppsView.vue'),
  },
  {
    path: '/account',
    name: 'account',
    component: () => import('@/features/account/views/AccountView.vue'),
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/features/admin/views/AdminView.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  // When a RouterLink targets an in-page hash (e.g. { path: '/', hash: '#about' })
  // from any route, scroll the destination element into view after navigation.
  // The top offset accounts for the sticky nav bar.
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth', top: 80 }
    return { left: 0, top: 0 }
  },
})
