<script setup lang="ts">
import type { LoginFormData } from '../schemas/loginSchema'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { loginSchema } from '../schemas/loginSchema'
import { resendConfirmation } from '../services/authService'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = ref<LoginFormData>({ username: '', password: '' })
const totpCode = ref('')
const fieldErrors = ref<Partial<Record<keyof LoginFormData, string>>>({})

const resendEmail = ref('')
const resending = ref(false)
const resendSuccess = ref('')
const resendError = ref('')

const isEmailNotConfirmed = computed(() => {
  const err = authStore.error
  if (!err) return false
  return typeof err === 'string' && err.toLowerCase().includes('not been confirmed')
})

async function onResendConfirmation(): Promise<void> {
  if (!resendEmail.value || resending.value) return
  resending.value = true
  resendSuccess.value = ''
  resendError.value = ''
  try {
    await resendConfirmation(resendEmail.value)
    resendSuccess.value = 'Confirmation email sent! Check your inbox.'
  } catch {
    resendError.value = 'Failed to resend confirmation email. Please try again.'
  } finally {
    resending.value = false
  }
}

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

function redirectAfterLogin(): void {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
  if (redirect) {
    window.location.href = redirect
  } else {
    router.push({ name: 'totp-setup' })
  }
}

async function onSubmit(): Promise<void> {
  if (!validate()) return
  try {
    await authStore.login(form.value.username, form.value.password)
    if (!authStore.totpRequired) {
      redirectAfterLogin()
    }
  } catch {
    // error is set on the store
  }
}

async function onTotpSubmit(): Promise<void> {
  if (totpCode.value.length !== 6) return
  try {
    await authStore.verifyTotpChallenge(totpCode.value)
    redirectAfterLogin()
  } catch {
    totpCode.value = ''
  }
}
</script>

<template>
  <form
    class="w-full max-w-md space-y-5 rounded-xl border border-surface-border bg-surface-card p-8"
    @submit.prevent="authStore.totpRequired ? onTotpSubmit() : onSubmit()"
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

    <h1 class="text-2xl font-bold text-gray-100">
      {{ authStore.totpRequired ? 'Two-factor authentication' : 'Sign in' }}
    </h1>

    <!-- TOTP challenge step -->
    <template v-if="authStore.totpRequired">
      <p class="text-sm text-gray-400">Enter the 6-digit code from your authenticator app.</p>
      <div>
        <label class="block font-mono text-xs font-medium text-gray-400" for="totp-code"> Code </label>
        <input
          id="totp-code"
          v-model="totpCode"
          autocomplete="one-time-code"
          class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 text-center font-mono text-lg tracking-widest text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
          inputmode="numeric"
          maxlength="6"
          pattern="\d{6}"
          placeholder="000000"
          type="text"
        />
      </div>
    </template>

    <!-- Login step -->
    <template v-else>
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
        <label class="block font-mono text-xs font-medium text-gray-400" for="password"> Password </label>
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
    </template>

    <p v-if="authStore.error" class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
      {{ authStore.error }}
    </p>

    <!-- Resend confirmation when email not confirmed -->
    <div v-if="isEmailNotConfirmed" class="space-y-3 rounded-md border border-surface-border bg-surface-elevated p-4">
      <p class="text-sm text-gray-400">Enter your email to receive a new confirmation link.</p>
      <input
        v-model="resendEmail"
        class="block w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        placeholder="you@example.com"
        type="email"
      />
      <p
        v-if="resendSuccess"
        class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-2 text-sm text-terminal-green"
      >
        {{ resendSuccess }}
      </p>
      <p v-if="resendError" class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
        {{ resendError }}
      </p>
      <button
        :disabled="resending || !resendEmail"
        class="w-full rounded-md border border-accent bg-transparent px-4 py-2 font-mono text-sm font-semibold text-accent transition-colors hover:bg-accent/10 disabled:opacity-50"
        type="button"
        @click="onResendConfirmation"
      >
        {{ resending ? 'Sending...' : 'Resend confirmation email' }}
      </button>
    </div>

    <button
      :disabled="authStore.isLoading"
      class="glow-accent w-full rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light disabled:opacity-50"
      type="submit"
    >
      {{ authStore.isLoading ? 'Verifying...' : authStore.totpRequired ? 'Verify' : 'Sign in' }}
    </button>

    <p v-if="!authStore.totpRequired" class="text-center text-sm text-gray-500">
      Don't have an account?
      <router-link class="font-medium text-accent-light hover:underline" to="/register"> Register </router-link>
    </p>
  </form>
</template>
