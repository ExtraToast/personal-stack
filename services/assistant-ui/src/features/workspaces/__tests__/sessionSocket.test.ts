import type { SessionSocket } from '../services/sessionSocket'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { attachSessionSocket } from '../services/sessionSocket'

// Minimal driveable WebSocket double. The constants must live as
// statics because sessionSocket reads WebSocket.OPEN / CONNECTING / etc.
class MockWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3
  static instances: MockWebSocket[] = []

  readyState = MockWebSocket.CONNECTING
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((ev: { data: string }) => void) | null = null
  onclose: ((ev: { code: number; reason: string }) => void) | null = null

  constructor(public url: string) {
    MockWebSocket.instances.push(this)
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.({ code: 1000, reason: 'client' })
  }

  open(): void {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  serverClose(code = 1006): void {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.({ code, reason: 'idle' })
  }

  message(data: string): void {
    this.onmessage?.({ data })
  }
}

function latest(): MockWebSocket {
  const ws = MockWebSocket.instances.at(-1)
  if (!ws) throw new Error('no MockWebSocket created yet')
  return ws
}

describe('sessionSocket', () => {
  let sock: SessionSocket | null = null

  beforeEach(() => {
    vi.useFakeTimers()
    MockWebSocket.instances = []
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    sock?.close()
    sock = null
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('opens one socket on attach', () => {
    sock = attachSessionSocket({ sessionId: 's1', onOutput: () => {} })
    expect(MockWebSocket.instances).toHaveLength(1)
    expect(latest().url).toContain('/api/v1/ws/sessions/s1/attach')
  })

  it('queues input typed before open and flushes it once open', () => {
    sock = attachSessionSocket({ sessionId: 's1', onOutput: () => {} })
    sock.send('hi', false)
    expect(latest().sent).toHaveLength(0) // still CONNECTING
    latest().open()
    expect(latest().sent).toEqual([JSON.stringify({ input: 'hi', enter: false })])
  })

  it('parses {output} frames to onOutput and ignores other frames', () => {
    const out: string[] = []
    sock = attachSessionSocket({ sessionId: 's1', onOutput: (t) => out.push(t) })
    latest().open()
    latest().message(JSON.stringify({ output: 'hello' }))
    latest().message(JSON.stringify({ other: 'x' }))
    latest().message('not-json')
    expect(out).toEqual(['hello'])
  })

  it('reconnects after an unexpected close and fires onReopen on the new open', () => {
    const reopened = vi.fn()
    sock = attachSessionSocket({ sessionId: 's1', onOutput: () => {}, onReopen: reopened })
    latest().open()
    expect(MockWebSocket.instances).toHaveLength(1)

    latest().serverClose() // idle drop
    vi.advanceTimersByTime(600) // past first backoff
    expect(MockWebSocket.instances).toHaveLength(2)

    expect(reopened).not.toHaveBeenCalled()
    latest().open() // reconnect established
    expect(reopened).toHaveBeenCalledTimes(1)
  })

  it('does not reconnect while reconnect is disabled', () => {
    sock = attachSessionSocket({ sessionId: 's1', onOutput: () => {} })
    latest().open()
    sock.setReconnect(false)
    latest().serverClose()
    vi.advanceTimersByTime(60_000)
    expect(MockWebSocket.instances).toHaveLength(1)
  })

  it('reconnectNow reopens a lapsed socket immediately', () => {
    sock = attachSessionSocket({ sessionId: 's1', onOutput: () => {} })
    latest().open()
    sock.setReconnect(false)
    latest().serverClose()
    expect(MockWebSocket.instances).toHaveLength(1)

    sock.setReconnect(true)
    sock.reconnectNow()
    expect(MockWebSocket.instances).toHaveLength(2)
  })

  it('never reconnects after a deliberate close', () => {
    sock = attachSessionSocket({ sessionId: 's1', onOutput: () => {} })
    latest().open()
    sock.close()
    vi.advanceTimersByTime(60_000)
    expect(MockWebSocket.instances).toHaveLength(1)
  })
})
