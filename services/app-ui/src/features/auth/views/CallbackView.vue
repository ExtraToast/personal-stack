<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const error = ref<string | null>(null)

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const state = params.get('state')
  const errorParam = params.get('error')

  if (errorParam) {
    error.value = `Authentication failed: ${errorParam}`
    return
  }

  if (!code || !state) {
    error.value = 'Invalid callback — missing code or state'
    return
  }

  try {
    await authStore.handleCallback(code, state)
    await router.replace('/')
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Authentication failed'
  }
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center">
    <div v-if="error" class="text-center font-mono">
      <p class="text-red-400">{{ error }}</p>
      <a class="mt-4 text-terminal-green hover:underline" href="/">Return home</a>
    </div>
    <div v-else class="text-center font-mono text-gray-400">
      <p>Completing sign-in…</p>
    </div>
  </div>
</template>
