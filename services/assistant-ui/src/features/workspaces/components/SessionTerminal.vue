<script setup lang="ts">
import type { SessionSocket } from '../services/sessionSocket'
import { FitAddon } from '@xterm/addon-fit'
import { Terminal } from '@xterm/xterm'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { attachSessionSocket } from '../services/sessionSocket'
import '@xterm/xterm/css/xterm.css'

const props = defineProps<{ sessionId: string; active?: boolean }>()

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
    // A reconnect re-attaches and the gateway resends a current-screen
    // snapshot; reset first so it repaints cleanly instead of stacking
    // under the stale buffer.
    onReopen: () => term?.reset(),
  })
  // Only the visible terminal keeps its socket warm; a hidden tab is
  // allowed to lapse so it does not pin its runner against the idle
  // reaper, then reconnects when shown again.
  socket.setReconnect(props.active ?? false)

  // xterm emits raw keystroke bytes (incl. "\r"); the gateway's
  // send-keys -l passes them literally, so enter is always false.
  term.onData((data) => socket?.send(data, false))

  term.onResize(({ cols, rows }) => {
    socket?.sendResize(cols, rows)
  })

  resizeObserver = new ResizeObserver(() => fitAndReportSize())
  resizeObserver.observe(el)
  window.addEventListener('resize', fitAndReportSize)

  if (props.active) revealAndFocus()
})

// While a terminal is hidden via `display:none` (v-show on an
// inactive tab) xterm cannot measure its container, so fit() computed
// against the previous tab's geometry. Re-fit and focus once the tab
// becomes visible again so the PTY size matches the viewport.
function revealAndFocus(): void {
  if (!term) return
  fitAndReportSize()
  term.focus()
}

watch(
  () => props.active,
  (isActive) => {
    socket?.setReconnect(isActive ?? false)
    if (isActive) {
      // Returning to a tab whose socket lapsed while hidden: bring it
      // back immediately rather than waiting for a keystroke to notice.
      socket?.reconnectNow()
      revealAndFocus()
    }
  },
)

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
