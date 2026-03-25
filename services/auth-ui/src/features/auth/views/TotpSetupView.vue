<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TotpQrCode from '../components/TotpQrCode.vue'
import TotpVerifyForm from '../components/TotpVerifyForm.vue'
import { enrollTotp, verifyTotp } from '../services/authService'

const router = useRouter()

const totpData = ref<{ secret: string; qrUri: string } | null>(null)
const isLoading = ref(true)
const verifying = ref(false)
const verifyError = ref<string | null>(null)

onMounted(async () => {
  try {
    totpData.value = await enrollTotp()
  } finally {
    isLoading.value = false
  }
})

async function onVerify(code: string): Promise<void> {
  verifying.value = true
  verifyError.value = null
  try {
    await verifyTotp(code)
    await router.push({ name: 'dashboard' })
  } catch {
    verifyError.value = 'Code verification failed. Please try again.'
  } finally {
    verifying.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4">
    <div class="w-full max-w-md rounded-lg bg-white p-8 shadow-lg">
      <h1 class="mb-2 text-2xl font-bold text-gray-900">Set up two-factor authentication</h1>
      <p class="mb-6 text-sm text-gray-600">Protect your account with a TOTP authenticator app.</p>

      <div v-if="isLoading" class="py-8 text-center text-gray-500">Loading…</div>

      <template v-else-if="totpData">
        <TotpQrCode :qr-uri="totpData.qrUri" :secret="totpData.secret" />
        <div class="mt-6">
          <p class="mb-3 text-sm font-medium text-gray-700">
            After scanning, enter the 6-digit code to confirm:
          </p>
          <TotpVerifyForm @submit="onVerify" />
          <p v-if="verifyError" class="mt-2 text-sm text-red-600">
            {{ verifyError }}
          </p>
        </div>
      </template>
    </div>
  </div>
</template>
