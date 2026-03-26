<script setup lang="ts">
import type { LoginFormData } from '../schemas/loginSchema'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { loginSchema } from '../schemas/loginSchema'

const router = useRouter()
const authStore = useAuthStore()

const form = ref<LoginFormData>({ username: '', password: '' })
const fieldErrors = ref<Partial<Record<keyof LoginFormData, string>>>({})

function validate(): boolean {
  const result = loginSchema.safeParse(form.value)
  if (result.success) {
    fieldErrors.value = {}
    return true
  }
  const mapped: Partial<Record<string, string>> = {}
  for (const e of result.error.errors) {
    const key = String(e.path[0])
    if (key) mapped[key] = e.message
  }
  fieldErrors.value = mapped
  return false
}

async function onSubmit(): Promise<void> {
  if (!validate()) return
  try {
    await authStore.login(form.value.username, form.value.password)
    await router.push({ name: 'dashboard' })
  } catch {
    // error is set on the store
  }
}
</script>

<template>
  <form
    class="w-full max-w-md space-y-5 rounded-xl border border-surface-border bg-surface-card p-8"
    @submit.prevent="onSubmit"
  >
    <!-- Terminal-style header -->
    <div class="flex items-center gap-2">
      <div class="flex gap-1.5">
        <div class="h-2.5 w-2.5 rounded-full bg-red-500/60" />
        <div class="h-2.5 w-2.5 rounded-full bg-terminal-amber/60" />
        <div class="h-2.5 w-2.5 rounded-full bg-terminal-green/60" />
      </div>
      <span class="font-mono text-xs text-gray-600"> ~/auth/login </span>
    </div>

    <h1 class="text-2xl font-bold text-gray-100">Sign in</h1>

    <div>
      <label class="block font-mono text-xs font-medium text-gray-400" for="username">
        Username
      </label>
      <input
        id="username"
        v-model="form.username"
        autocomplete="username"
        class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        placeholder="your-username"
        type="text"
      />
      <p v-if="fieldErrors.username" class="mt-1 text-sm text-red-400">
        {{ fieldErrors.username }}
      </p>
    </div>

    <div>
      <label class="block font-mono text-xs font-medium text-gray-400" for="password">
        Password
      </label>
      <input
        id="password"
        v-model="form.password"
        autocomplete="current-password"
        class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        placeholder="••••••••"
        type="password"
      />
      <p v-if="fieldErrors.password" class="mt-1 text-sm text-red-400">
        {{ fieldErrors.password }}
      </p>
    </div>

    <p
      v-if="authStore.error"
      class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400"
    >
      {{ authStore.error }}
    </p>

    <button
      :disabled="authStore.isLoading"
      class="glow-accent w-full rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light disabled:opacity-50"
      type="submit"
    >
      {{ authStore.isLoading ? 'Signing in...' : 'Sign in' }}
    </button>

    <p class="text-center text-sm text-gray-500">
      Don't have an account?
      <router-link class="font-medium text-accent-light hover:underline" to="/register">
        Register
      </router-link>
    </p>
  </form>
</template>
