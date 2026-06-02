/**
 * Thin wrapper around the browser WebSocket for the
 * `/api/v1/ws/sessions/{id}/attach` endpoint. The assistant-api
 * handler speaks two envelope shapes:
 *
 *   inbound  -> `{ "input": "...", "enter": true }`
 *   inbound  -> `{ "resize": { "cols": <int>, "rows": <int> } }`
 *   outbound -> `{ "output": "...bytes-as-utf8..." }`
 *
 * The socket self-heals. A backgrounded tab's WS is idle-closed by the
 * proxy/container after a while; without reconnection the terminal
 * silently rots — output stops and `send` drops keystrokes because the
 * socket is no longer OPEN. So an unexpected close schedules a
 * capped-backoff reconnect, keystrokes typed across the gap are queued
 * and flushed on reopen, and `onReopen` fires so the caller can clear
 * the screen before the gateway's fresh attach-snapshot repaints it
 * (otherwise the snapshot would append under the stale buffer).
 *
 * Reconnection is gated by [setReconnect] so an inactive tab does not
 * hold its runner alive against the idle reaper: only the visible
 * terminal keeps its socket warm; a hidden one is allowed to lapse and
 * reconnects when it is shown again.
 */
export interface SessionSocketOptions {
  sessionId: string
  onOutput: (text: string) => void
  /** Fired when a *reconnect* (not the first connect) opens. */
  onReopen?: () => void
  onClose?: (code: number, reason: string) => void
}

export interface SessionSocket {
  send: (input: string, enter?: boolean) => void
  sendKey: (key: string) => void
  sendResize: (cols: number, rows: number) => void
  /** Enable/disable auto-reconnect (drive from tab visibility). */
  setReconnect: (enabled: boolean) => void
  /** Reconnect immediately if the socket is not already live. */
  reconnectNow: () => void
  close: () => void
  readyState: () => number
}

const MAX_BACKOFF_MS = 10_000
const BASE_BACKOFF_MS = 500
const MAX_QUEUED_FRAMES = 200

export function attachSessionSocket(opts: SessionSocketOptions): SessionSocket {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${proto}//${window.location.host}/api/v1/ws/sessions/${opts.sessionId}/attach`

  let ws: WebSocket | null = null
  let closedByCaller = false
  let reconnectEnabled = true
  let attempts = 0
  let everOpened = false
  let timer: ReturnType<typeof setTimeout> | null = null
  const queue: string[] = []

  function clearTimer(): void {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function flushQueue(): void {
    while (queue.length > 0 && ws?.readyState === WebSocket.OPEN) {
      const frame = queue.shift()
      if (frame !== undefined) ws.send(frame)
    }
  }

  function enqueue(frame: string): void {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(frame)
    } else if (queue.length < MAX_QUEUED_FRAMES) {
      queue.push(frame)
    }
  }

  function scheduleReconnect(): void {
    if (!reconnectEnabled || closedByCaller) return
    clearTimer()
    const delay = Math.min(BASE_BACKOFF_MS * 2 ** attempts, MAX_BACKOFF_MS)
    attempts += 1
    timer = setTimeout(connect, delay)
  }

  function connect(): void {
    clearTimer()
    if (closedByCaller) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return

    ws = new WebSocket(url)
    ws.onopen = () => {
      attempts = 0
      if (everOpened) opts.onReopen?.()
      everOpened = true
      flushQueue()
    }
    ws.onmessage = (ev) => {
      try {
        const payload: unknown = JSON.parse(ev.data)
        if (payload && typeof payload === 'object' && 'output' in payload) {
          const output = (payload as { output: unknown }).output // eslint-disable-line ts/consistent-type-assertions
          if (typeof output === 'string') opts.onOutput(output)
        }
      } catch {
        // ignore non-JSON frames
      }
    }
    ws.onclose = (ev) => {
      opts.onClose?.(ev.code, ev.reason)
      scheduleReconnect()
    }
  }

  connect()

  return {
    send(input, enter = true) {
      enqueue(JSON.stringify({ input, enter }))
    },
    sendKey(key) {
      enqueue(JSON.stringify({ input: key, enter: false }))
    },
    sendResize(cols, rows) {
      enqueue(JSON.stringify({ resize: { cols, rows } }))
    },
    setReconnect(enabled) {
      reconnectEnabled = enabled
      if (!enabled) clearTimer()
    },
    reconnectNow() {
      attempts = 0
      const state = ws?.readyState
      if (state === undefined || state === WebSocket.CLOSED || state === WebSocket.CLOSING) {
        connect()
      }
    },
    close() {
      closedByCaller = true
      clearTimer()
      ws?.close()
    },
    readyState() {
      return ws?.readyState ?? WebSocket.CLOSED
    },
  }
}
