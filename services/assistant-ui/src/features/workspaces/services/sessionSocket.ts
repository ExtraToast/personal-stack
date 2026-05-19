/**
 * Thin wrapper around the browser WebSocket for the
 * `/api/v1/ws/sessions/{id}/attach` endpoint. The assistant-api
 * handler speaks two envelope shapes:
 *
 *   inbound  -> `{ "input": "...", "enter": true }`
 *   outbound -> `{ "output": "...bytes-as-utf8..." }`
 *
 * This wrapper hides the JSON encoding from the store and emits
 * raw strings via the `onOutput` callback.
 */
export interface SessionSocketOptions {
  sessionId: string
  onOutput: (text: string) => void
  onClose?: (code: number, reason: string) => void
}

export interface SessionSocket {
  send: (input: string, enter?: boolean) => void
  sendKey: (key: string) => void
  close: () => void
  readyState: () => number
}

export function attachSessionSocket(opts: SessionSocketOptions): SessionSocket {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${proto}//${window.location.host}/api/v1/ws/sessions/${opts.sessionId}/attach`
  const ws = new WebSocket(url)

  ws.onmessage = (ev) => {
    try {
      const payload = JSON.parse(ev.data) as { output?: string }
      if (typeof payload.output === 'string') opts.onOutput(payload.output)
    } catch {
      // ignore non-JSON frames
    }
  }
  ws.onclose = (ev) => opts.onClose?.(ev.code, ev.reason)

  return {
    send(input, enter = true) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ input, enter }))
      }
    },
    sendKey(key) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ input: key, enter: false }))
      }
    },
    close() {
      ws.close()
    },
    readyState() {
      return ws.readyState
    },
  }
}
