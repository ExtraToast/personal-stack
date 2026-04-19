<script setup lang="ts">
import QRCode from 'qrcode'
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{
  secret: string
  qrUri: string
}>()

const canvas = ref<HTMLCanvasElement | null>(null)

async function renderQr(): Promise<void> {
  if (canvas.value && props.qrUri) {
    await QRCode.toCanvas(canvas.value, props.qrUri, {
      width: 200,
      margin: 2,
      color: { dark: '#000000', light: '#ffffff' },
    })
  }
}

onMounted(renderQr)
watch(() => props.qrUri, renderQr)
</script>

<template>
  <div class="space-y-4 text-center">
    <p class="text-sm text-gray-400">Scan the QR code with Google Authenticator, Authy, or any TOTP app.</p>

    <canvas ref="canvas" class="mx-auto rounded-lg border border-surface-border bg-white p-2" />

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
