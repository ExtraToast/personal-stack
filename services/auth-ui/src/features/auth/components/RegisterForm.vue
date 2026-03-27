<script setup lang="ts">
import type { RegisterFormData } from '../schemas/registerSchema'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { registerSchema } from '../schemas/registerSchema'

const router = useRouter()
const authStore = useAuthStore()

const form = ref<RegisterFormData>({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})
const fieldErrors = ref<Partial<Record<keyof RegisterFormData, string>>>({})

function validate(): boolean {
  const result = registerSchema.safeParse(form.value)
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
    await authStore.register(form.value.username, form.value.email, form.value.password)
    await router.push({ name: 'check-email', query: { email: form.value.email } })
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
      <span class="font-mono text-xs text-gray-600"> ~/auth/register </span>
    </div>

    <h1 class="text-2xl font-bold text-gray-100">Create account</h1>

    <div>
      <label class="block font-mono text-xs font-medium text-gray-400" for="username"> Username </label>
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
      <label class="block font-mono text-xs font-medium text-gray-400" for="email"> Email </label>
      <input
        id="email"
        v-model="form.email"
        autocomplete="email"
        class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        placeholder="you@example.com"
        type="email"
      />
      <p v-if="fieldErrors.email" class="mt-1 text-sm text-red-400">
        {{ fieldErrors.email }}
      </p>
    </div>

    <div>
      <label class="block font-mono text-xs font-medium text-gray-400" for="password"> Password </label>
      <input
        id="password"
        v-model="form.password"
        autocomplete="new-password"
        class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        placeholder="Min. 8 characters"
        type="password"
      />
      <p v-if="fieldErrors.password" class="mt-1 text-sm text-red-400">
        {{ fieldErrors.password }}
      </p>
    </div>

    <div>
      <label class="block font-mono text-xs font-medium text-gray-400" for="confirmPassword"> Confirm password </label>
      <input
        id="confirmPassword"
        v-model="form.confirmPassword"
        autocomplete="new-password"
        class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        placeholder="••••••••"
        type="password"
      />
      <p v-if="fieldErrors.confirmPassword" class="mt-1 text-sm text-red-400">
        {{ fieldErrors.confirmPassword }}
      </p>
    </div>

    <p v-if="authStore.error" class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
      {{ authStore.error }}
    </p>

    <button
      :disabled="authStore.isLoading"
      class="glow-accent w-full rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light disabled:opacity-50"
      type="submit"
    >
      {{ authStore.isLoading ? 'Creating account...' : 'Create account' }}
    </button>

    <p class="text-center text-sm text-gray-500">
      Already have an account?
      <router-link class="font-medium text-accent-light hover:underline" to="/login"> Sign in </router-link>
    </p>
  </form>
</template>
