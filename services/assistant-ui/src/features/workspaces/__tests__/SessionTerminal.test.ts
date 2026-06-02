import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SessionTerminal from '../components/SessionTerminal.vue'
import { attachSessionSocket } from '../services/sessionSocket'

// xterm touches real DOM/canvas APIs jsdom does not implement, so the
// Terminal + FitAddon are stubbed. The stub captures the data/resize
// handlers and exposes the write spy so both directions can be asserted.
let onDataCb: ((data: string) => void) | undefined
let onResizeCb: ((e: { cols: number; rows: number }) => void) | undefined
const term = {
  write: vi.fn(),
  loadAddon: vi.fn(),
  open: vi.fn(),
  onData: vi.fn((cb: (data: string) => void) => {
    onDataCb = cb
  }),
  onResize: vi.fn((cb: (e: { cols: number; rows: number }) => void) => {
    onResizeCb = cb
  }),
  focus: vi.fn(),
  dispose: vi.fn(),
}
vi.mock('@xterm/xterm', () => ({
  Terminal: class {
    write = term.write
    loadAddon = term.loadAddon
    open = term.open
    onData = term.onData
    onResize = term.onResize
    focus = term.focus
    dispose = term.dispose
  },
}))
vi.mock('@xterm/xterm/css/xterm.css', () => ({}))
vi.mock('@xterm/addon-fit', () => ({
  FitAddon: class {
    fit = vi.fn()
  },
}))

const socket = {
  send: vi.fn(),
  sendKey: vi.fn(),
  sendResize: vi.fn(),
  close: vi.fn(),
  readyState: vi.fn(() => 1),
}
let capturedOnOutput: ((text: string) => void) | undefined
vi.mock('../services/sessionSocket', () => ({
  attachSessionSocket: vi.fn((opts: { onOutput: (t: string) => void }) => {
    capturedOnOutput = opts.onOutput
    return socket
  }),
}))

describe('sessionTerminal', () => {
  beforeEach(() => {
    Object.values(term).forEach((m) => m.mockClear())
    Object.values(socket).forEach((m) => m.mockClear())
    vi.mocked(attachSessionSocket).mockClear()
    capturedOnOutput = undefined
    onDataCb = undefined
    onResizeCb = undefined
  })

  afterEach(() => {
    vi.stubGlobal('ResizeObserver', undefined)
  })

  function mountTerminal(props: { sessionId?: string; active?: boolean } = {}) {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    )
    return mount(SessionTerminal, { props: { sessionId: 'sess-1', ...props } })
  }

  it('writes inbound output frames to the terminal', () => {
    mountTerminal()
    expect(attachSessionSocket).toHaveBeenCalledWith(expect.objectContaining({ sessionId: 'sess-1' }))
    capturedOnOutput?.('[31mhello[0m')
    expect(term.write).toHaveBeenCalledWith('[31mhello[0m')
  })

  it('forwards terminal keystrokes as input frames with enter=false', () => {
    mountTerminal()
    onDataCb?.('l')
    onDataCb?.('\r')
    expect(socket.send).toHaveBeenCalledWith('l', false)
    expect(socket.send).toHaveBeenCalledWith('\r', false)
  })

  it('forwards terminal resize as a resize frame', () => {
    mountTerminal()
    onResizeCb?.({ cols: 120, rows: 40 })
    expect(socket.sendResize).toHaveBeenCalledWith(120, 40)
  })

  it('closes the socket and disposes the terminal on unmount', () => {
    const wrapper = mountTerminal()
    wrapper.unmount()
    expect(socket.close).toHaveBeenCalled()
    expect(term.dispose).toHaveBeenCalled()
  })

  it('focuses the terminal when mounted active', () => {
    mountTerminal({ active: true })
    expect(term.focus).toHaveBeenCalled()
  })

  it('does not focus the terminal when mounted inactive', () => {
    mountTerminal({ active: false })
    expect(term.focus).not.toHaveBeenCalled()
  })

  it('focuses the terminal when it becomes active without re-attaching the socket', async () => {
    const wrapper = mountTerminal({ active: false })
    expect(attachSessionSocket).toHaveBeenCalledTimes(1)
    expect(term.focus).not.toHaveBeenCalled()

    await wrapper.setProps({ active: true })

    expect(term.focus).toHaveBeenCalled()
    // The socket is attached once at mount; toggling active must not
    // tear it down or open a new one.
    expect(attachSessionSocket).toHaveBeenCalledTimes(1)
    expect(socket.close).not.toHaveBeenCalled()
  })
})
