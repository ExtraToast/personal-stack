<script setup lang="ts">
import type { SessionSocket } from '../services/sessionSocket'
import { FitAddon } from '@xterm/addon-fit'
import { Terminal } from '@xterm/xterm'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { attachSessionSocket } from '../services/sessionSocket'
import '@xterm/xterm/css/xterm.css'

const props = defineProps<{ sessionId: string }>()

const container = ref<HTMLDivElement | null>(null)

let term: Terminal | null = null
let fitAddon: FitAddon | null = null
let socket: SessionSocket | null = null
let resizeObserver: ResizeObserver | null = null

function fitAndReportSize(): void {
  if (!fitAddon || !term) return
  fitAddon.fit()
  // The gateway sizes the PTY from the RESIZE frame; xterm's own
  // onResize fires from fit(), so the report happens via that handler.
}

onMounted(() => {
  const el = container.value
  if (!el) return

  term = new Terminal({
    convertEol: false,
    cursorBlink: true,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    fontSize: 13,
    theme: { background: '#0b0e14' },
  })
  fitAddon = new FitAddon()
  term.loadAddon(fitAddon)
  term.open(el)
  fitAddon.fit()

  socket = attachSessionSocket({
    sessionId: props.sessionId,
    onOutput: (text) => term?.write(text),
  })

  // xterm emits raw keystroke bytes (incl. "\r"); the gateway's
  // send-keys -l passes them literally, so enter is always false.
  term.onData((data) => socket?.send(data, false))

  term.onResize(({ cols, rows }) => {
    socket?.sendResize(cols, rows)
  })

  resizeObserver = new ResizeObserver(() => fitAndReportSize())
  resizeObserver.observe(el)
  window.addEventListener('resize', fitAndReportSize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', fitAndReportSize)
  resizeObserver?.disconnect()
  resizeObserver = null
  socket?.close()
  socket = null
  term?.dispose()
  term = null
  fitAddon = null
})
</script>

<template>
  <div ref="container" class="flex-1 min-h-0 overflow-hidden rounded bg-[#0b0e14] p-2" data-testid="session-terminal" />
</template>
