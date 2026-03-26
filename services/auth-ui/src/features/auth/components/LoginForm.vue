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
    class="w-full max-w-md space-y-5 rounded-lg bg-white p-8 shadow-lg dark:bg-gray-900"
    @submit.prevent="onSubmit"
  >
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">Sign in</h1>

    <div>
      <label class="block text-sm font-medium text-gray-700" for="username"> Username </label>
      <input
        id="username"
        v-model="form.username"
        autocomplete="username"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
        placeholder="your-username"
        type="text"
      />
      <p v-if="fieldErrors.username" class="mt-1 text-sm text-red-600">
        {{ fieldErrors.username }}
      </p>
    </div>

    <div>
      <label class="block text-sm font-medium text-gray-700" for="password"> Password </label>
      <input
        id="password"
        v-model="form.password"
        autocomplete="current-password"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
        placeholder="••••••••"
        type="password"
      />
      <p v-if="fieldErrors.password" class="mt-1 text-sm text-red-600">
        {{ fieldErrors.password }}
      </p>
    </div>

    <p v-if="authStore.error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
      {{ authStore.error }}
    </p>

    <button
      :disabled="authStore.isLoading"
      class="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800 disabled:opacity-50"
      type="submit"
    >
      {{ authStore.isLoading ? 'Signing in…' : 'Sign in' }}
    </button>

    <p class="text-center text-sm text-gray-600">
      Don't have an account?
      <router-link class="font-medium text-gray-900 underline" to="/register">
        Register
      </router-link>
    </p>
  </form>
</template>
