<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/features/auth'
import { getAccessibleServices } from '../data/serviceRegistry'
import AppCard from './AppCard.vue'

const authStore = useAuthStore()
const services = computed(() => getAccessibleServices(authStore.servicePermissions))
</script>

<template>
  <section v-if="services.length > 0" class="mx-auto max-w-6xl px-6 py-12">
    <h2 class="mb-6 font-mono text-sm font-bold uppercase tracking-widest text-terminal-green">My Apps</h2>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <AppCard v-for="service in services" :key="service.permission" :service="service" />
    </div>
  </section>
</template>
