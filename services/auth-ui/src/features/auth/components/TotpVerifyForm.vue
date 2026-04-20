<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { z } from 'zod'

const emit = defineEmits<{
  submit: [code: string]
}>()

const codeSchema = z.string().regex(/^\d{6}$/, 'Must be exactly 6 digits')

const code = ref('')
const error = ref<string | null>(null)
const codeInput = ref<HTMLInputElement | null>(null)

async function focusCodeInput(): Promise<void> {
  await nextTick()
  codeInput.value?.focus()
  codeInput.value?.select()
}

onMounted(() => {
  void focusCodeInput()
})

function onSubmit(): void {
  const result = codeSchema.safeParse(code.value)
  if (!result.success) {
    error.value = result.error.issues[0]?.message ?? 'Invalid code'
    return
  }
  error.value = null
  emit('submit', code.value)
}
</script>

<template>
  <form class="space-y-4" @submit.prevent="onSubmit">
    <div>
      <label class="block font-mono text-xs font-medium text-gray-400" for="totp-code">
        6-digit code from your authenticator app
      </label>
      <input
        id="totp-code"
        ref="codeInput"
        v-model="code"
        autocomplete="one-time-code"
        class="mt-1 block w-full rounded-md border border-surface-border bg-surface-elevated px-3 py-2 text-center font-mono text-xl tracking-widest text-gray-200 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        inputmode="numeric"
        maxlength="6"
        pattern="\d{6}"
        placeholder="000000"
        type="text"
      />
      <p v-if="error" class="mt-1 text-sm text-red-400">
        {{ error }}
      </p>
    </div>
    <button
      class="glow-accent w-full rounded-md bg-accent px-4 py-2 font-mono text-sm font-semibold text-white transition-colors hover:bg-accent-light"
      type="submit"
    >
      Verify
    </button>
  </form>
</template>
