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
    path: '/admin',
    name: 'admin',
    component: () => import('@/features/admin/views/AdminView.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
