<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import AppsGrid from '../components/AppsGrid.vue'

const authStore = useAuthStore()
const router = useRouter()

onMounted(async () => {
  // Wait for the in-flight /me check before deciding to bounce to home.
  // Without this, a direct hit on /apps during the auth backend's cold
  // start would redirect the signed-in user away.
  await authStore.initSession()
  if (!authStore.isAuthenticated) {
    await router.replace('/')
  }
})
</script>

<template>
  <div class="min-h-screen pt-16">
    <AppsGrid />
    <div
      v-if="authStore.isAuthenticated && authStore.servicePermissions.length === 0"
      class="mx-auto max-w-6xl px-6 py-12 font-mono text-sm text-[var(--color-text-muted)]"
    >
      No services have been granted yet.
    </div>
  </div>
</template>
