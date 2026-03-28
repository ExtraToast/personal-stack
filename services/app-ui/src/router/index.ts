import type { RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/features/home/views/HomeView.vue'),
  },
  {
    path: '/callback',
    name: 'auth-callback',
    component: () => import('@/features/auth/views/CallbackView.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
