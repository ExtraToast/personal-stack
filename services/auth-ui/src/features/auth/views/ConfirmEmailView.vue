<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { confirmEmail, resendConfirmation } from '../services/authService'

const route = useRoute()
const token = typeof route.query.token === 'string' ? route.query.token : ''

const status = ref<'loading' | 'success' | 'error'>('loading')
const errorMessage = ref('')

const resendEmail = ref('')
const resending = ref(false)
const resendSuccess = ref('')
const resendError = ref('')

onMounted(async () => {
  if (!token) {
    status.value = 'error'
    errorMessage.value = 'No confirmation token provided.'
    return
  }
  try {
    await confirmEmail(token)
    status.value = 'success'
  } catch {
    status.value = 'error'
    errorMessage.value = 'This confirmation link is invalid or has expired.'
  }
})

async function onResend(): Promise<void> {
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
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-surface-dark px-4">
    <div class="w-full max-w-md space-y-5 rounded-xl border border-surface-border bg-surface-card p-8">
      <!-- Terminal-style header -->
      <div class="flex items-center gap-2">
        <div class="flex gap-1.5">
          <div class="h-2.5 w-2.5 rounded-full bg-red-500/60" />
          <div class="h-2.5 w-2.5 rounded-full bg-terminal-amber/60" />
          <div class="h-2.5 w-2.5 rounded-full bg-terminal-green/60" />
        </div>
        <span class="font-mono text-xs text-gray-600"> ~/auth/confirm-email </span>
      </div>

      <!-- Loading state -->
      <template v-if="status === 'loading'">
        <h1 class="text-2xl font-bold text-gray-100">Confirming your email...</h1>
        <p class="text-sm text-gray-400">Please wait while we verify your confirmation link.</p>
      </template>

      <!-- Success state -->
      <template v-else-if="status === 'success'">
        <h1 class="text-2xl font-bold text-gray-100">Email confirmed!</h1>
        <p class="text-sm text-gray-400">Your email has been confirmed. You can now sign in.</p>
        <router-link
          class="glow-accent block w-full rounded-md bg-accent px-4 py-2 text-center font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light"
          to="/login"
        >
          Sign in
        </router-link>
      </template>

      <!-- Error state -->
      <template v-else>
        <h1 class="text-2xl font-bold text-gray-100">Confirmation failed</h1>
        <p class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
          {{ errorMessage }}
        </p>

        <div class="space-y-3">
          <p class="text-sm text-gray-400">Enter your email to receive a new confirmation link.</p>
          <div>
            <input
              v-model="resendEmail"
              class="block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              placeholder="you@example.com"
              type="email"
            />
          </div>

          <p
            v-if="resendSuccess"
            class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-2 text-sm text-terminal-green"
          >
            {{ resendSuccess }}
          </p>

          <p
            v-if="resendError"
            class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400"
          >
            {{ resendError }}
          </p>

          <button
            :disabled="resending || !resendEmail"
            class="glow-accent w-full rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light disabled:opacity-50"
            type="button"
            @click="onResend"
          >
            {{ resending ? 'Sending...' : 'Resend confirmation email' }}
          </button>
        </div>

        <p class="text-center text-sm text-gray-500">
          <router-link class="font-medium text-accent-light hover:underline" to="/login"> Back to login </router-link>
        </p>
      </template>
    </div>
  </div>
</template>
