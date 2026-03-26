<script setup lang="ts">
import { ref } from 'vue'
import { z } from 'zod'

const emit = defineEmits<{
  submit: [code: string]
}>()

const codeSchema = z.string().regex(/^\d{6}$/, 'Must be exactly 6 digits')

const code = ref('')
const error = ref<string | null>(null)

function onSubmit(): void {
  const result = codeSchema.safeParse(code.value)
  if (!result.success) {
    error.value = result.error.errors[0]?.message ?? 'Invalid code'
    return
  }
  error.value = null
  emit('submit', code.value)
}
</script>

<template>
  <form class="space-y-4" @submit.prevent="onSubmit">
    <div>
      <label class="block text-sm font-medium text-gray-700" for="totp-code">
        6-digit code from your authenticator app
      </label>
      <input
        id="totp-code"
        v-model="code"
        autocomplete="one-time-code"
        class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-center text-xl tracking-widest text-gray-900 focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        inputmode="numeric"
        maxlength="6"
        pattern="\d{6}"
        placeholder="000000"
        type="text"
      />
      <p v-if="error" class="mt-1 text-sm text-red-600">
        {{ error }}
      </p>
    </div>
    <button
      class="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800"
      type="submit"
    >
      Verify
    </button>
  </form>
</template>
