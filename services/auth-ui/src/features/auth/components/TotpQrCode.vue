<script setup lang="ts">
defineProps<{
  secret: string
  qrUri: string
}>()
</script>

<template>
  <div class="space-y-4 text-center">
    <p class="text-sm text-gray-600">
      Scan the QR code with Google Authenticator, Authy, or any TOTP app.
    </p>

    <!-- QR code rendered via a free QR API — URI is safe (otpauth scheme, no sensitive info beyond secret) -->
    <img
      alt="QR code for TOTP setup"
      :src="`https://api.qrserver.com/v1/create-qr-code/?data=${encodeURIComponent(qrUri)}&size=200x200`"
      class="mx-auto rounded-lg border border-gray-200 p-2"
      height="200"
      width="200"
    />

    <details class="text-left">
      <summary class="cursor-pointer text-sm text-gray-500">Or enter the key manually</summary>
      <code
        class="mt-2 block break-all rounded bg-gray-100 px-3 py-2 text-sm font-mono text-gray-800"
      >
        {{ secret }}
      </code>
    </details>
  </div>
</template>
