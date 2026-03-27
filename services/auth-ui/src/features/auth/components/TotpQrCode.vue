<script setup lang="ts">
defineProps<{
  secret: string
  qrUri: string
}>()
</script>

<template>
  <div class="space-y-4 text-center">
    <p class="text-sm text-gray-400">Scan the QR code with Google Authenticator, Authy, or any TOTP app.</p>

    <!-- QR code rendered via a free QR API -->
    <img
      alt="QR code for TOTP setup"
      :src="`https://api.qrserver.com/v1/create-qr-code/?data=${encodeURIComponent(qrUri)}&size=200x200`"
      class="mx-auto rounded-lg border border-surface-border bg-white p-2"
      height="200"
      width="200"
    />

    <details class="text-left">
      <summary class="cursor-pointer font-mono text-sm text-gray-500 hover:text-gray-300">
        Or enter the key manually
      </summary>
      <code
        class="mt-2 block break-all rounded-md border border-surface-border bg-surface-elevated px-3 py-2 font-mono text-sm text-terminal-green"
      >
        {{ secret }}
      </code>
    </details>
  </div>
</template>
