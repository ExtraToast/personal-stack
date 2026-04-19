<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { resendConfirmation } from '../services/authService'

const route = useRoute()
const email = typeof route.query.email === 'string' ? route.query.email : ''

const resending = ref(false)
const resendSuccess = ref('')
const resendError = ref('')

async function onResend(): Promise<void> {
  if (!email || resending.value) return
  resending.value = true
  resendSuccess.value = ''
  resendError.value = ''
  try {
    await resendConfirmation(email)
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
        <span class="font-mono text-xs text-gray-600"> ~/auth/check-email </span>
      </div>

      <h1 class="text-2xl font-bold text-gray-100">Check your inbox</h1>

      <p class="text-sm text-gray-400">
        We've sent a confirmation link to
        <span class="font-mono text-accent-light">{{ email }}</span
        >. Click the link to activate your account.
      </p>

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
        :disabled="resending || !email"
        class="glow-accent w-full rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light disabled:opacity-50"
        type="button"
        @click="onResend"
      >
        {{ resending ? 'Sending...' : 'Resend confirmation email' }}
      </button>

      <p class="text-center text-sm text-gray-500">
        <router-link class="font-medium text-accent-light hover:underline" to="/login"> Back to login </router-link>
      </p>
    </div>
  </div>
</template>
