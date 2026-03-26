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
    await authStore.login(form.value.username, form.value.password)
    await router.push({ name: 'totp-setup' })
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
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">Create account</h1>

    <div>
      <label class="block text-sm font-medium text-gray-700" for="username"> Username </label>
      <input
        id="username"
        v-model="form.username"
        autocomplete="username"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        placeholder="your-username"
        type="text"
      />
      <p v-if="fieldErrors.username" class="mt-1 text-sm text-red-600">
        {{ fieldErrors.username }}
      </p>
    </div>

    <div>
      <label class="block text-sm font-medium text-gray-700" for="email"> Email </label>
      <input
        id="email"
        v-model="form.email"
        autocomplete="email"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        placeholder="you@example.com"
        type="email"
      />
      <p v-if="fieldErrors.email" class="mt-1 text-sm text-red-600">
        {{ fieldErrors.email }}
      </p>
    </div>

    <div>
      <label class="block text-sm font-medium text-gray-700" for="password"> Password </label>
      <input
        id="password"
        v-model="form.password"
        autocomplete="new-password"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        placeholder="Min. 8 characters"
        type="password"
      />
      <p v-if="fieldErrors.password" class="mt-1 text-sm text-red-600">
        {{ fieldErrors.password }}
      </p>
    </div>

    <div>
      <label class="block text-sm font-medium text-gray-700" for="confirmPassword">
        Confirm password
      </label>
      <input
        id="confirmPassword"
        v-model="form.confirmPassword"
        autocomplete="new-password"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        placeholder="••••••••"
        type="password"
      />
      <p v-if="fieldErrors.confirmPassword" class="mt-1 text-sm text-red-600">
        {{ fieldErrors.confirmPassword }}
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
      {{ authStore.isLoading ? 'Creating account…' : 'Create account' }}
    </button>

    <p class="text-center text-sm text-gray-600">
      Already have an account?
      <router-link class="font-medium text-gray-900 underline" to="/login"> Sign in </router-link>
    </p>
  </form>
</template>
