<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TotpQrCode from '../components/TotpQrCode.vue'
import TotpVerifyForm from '../components/TotpVerifyForm.vue'
import { enrollTotp, verifyTotp } from '../services/authService'

const router = useRouter()

interface TotpData {
  secret: string
  qrUri: string
}

const totpData = ref<TotpData | null>(null)
const isLoading = ref(true)
const enrollError = ref<string | null>(null)
const verifying = ref(false)
const verifyError = ref<string | null>(null)

onMounted(async () => {
  try {
    totpData.value = await enrollTotp()
  } catch {
    enrollError.value = 'Failed to load TOTP setup. Please log in again.'
  } finally {
    isLoading.value = false
  }
})

async function onVerify(code: string): Promise<void> {
  verifying.value = true
  verifyError.value = null
  try {
    await verifyTotp(code)
    // Resume pending redirect (e.g. OAuth2 authorize URL) if stored by LoginForm.
    const pendingRedirect = sessionStorage.getItem('pendingRedirect')
    if (pendingRedirect) {
      sessionStorage.removeItem('pendingRedirect')
      window.location.href = pendingRedirect
      return
    }
    await router.push({ name: 'dashboard' })
  } catch {
    verifyError.value = 'Code verification failed. Please try again.'
  } finally {
    verifying.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-surface-dark px-4">
    <div class="w-full max-w-md rounded-xl border border-surface-border bg-surface-card p-8">
      <!-- Terminal-style header -->
      <div class="mb-6 flex items-center gap-2">
        <div class="flex gap-1.5">
          <div class="h-2.5 w-2.5 rounded-full bg-red-500/60" />
          <div class="h-2.5 w-2.5 rounded-full bg-terminal-amber/60" />
          <div class="h-2.5 w-2.5 rounded-full bg-terminal-green/60" />
        </div>
        <span class="font-mono text-xs text-gray-600"> ~/auth/totp-setup </span>
      </div>

      <h1 class="mb-2 text-xl font-bold text-gray-100">Set up two-factor authentication</h1>
      <p class="mb-6 text-sm text-gray-500">Protect your account with a TOTP authenticator app.</p>

      <div v-if="isLoading" class="py-8 text-center font-mono text-sm text-gray-500">Loading...</div>

      <div v-else-if="enrollError" class="py-8 text-center">
        <p class="text-sm text-red-400">{{ enrollError }}</p>
      </div>

      <template v-else-if="totpData">
        <TotpQrCode :qr-uri="totpData.qrUri" :secret="totpData.secret" />
        <div class="mt-6">
          <p class="mb-3 text-sm font-medium text-gray-400">After scanning, enter the 6-digit code to confirm:</p>
          <TotpVerifyForm @submit="onVerify" />
          <p v-if="verifyError" class="mt-2 text-sm text-red-400">
            {{ verifyError }}
          </p>
        </div>
      </template>
    </div>
  </div>
</template>
