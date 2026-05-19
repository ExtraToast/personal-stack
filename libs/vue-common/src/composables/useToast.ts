import { reactive, readonly } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  title: string
  body?: string
  /**
   * Auto-dismiss after this many ms. 0 = sticky (caller must dismiss
   * explicitly). Default 5_000.
   */
  durationMs: number
}

interface ToastInput {
  kind?: ToastKind
  title: string
  body?: string
  durationMs?: number
}

/**
 * Single global toast queue. `useToast()` returns a stable handle from
 * any component; the matching `<ToastHost>` (mounted once near the
 * app root) renders the queue.
 *
 * Why singleton: toasts are inherently cross-cutting. A form deep in
 * the tree wants to surface "saved" outside the form's own DOM, and
 * threading a `provide`/`inject` chain for that is needless ceremony.
 */
const state = reactive<{ toasts: Toast[] }>({ toasts: [] })

let nextId = 1

function push(input: ToastInput): Toast {
  // `exactOptionalPropertyTypes` requires us to omit the field rather
  // than set it to undefined when the input body is absent.
  const toast: Toast = {
    id: nextId++,
    kind: input.kind ?? 'info',
    title: input.title,
    durationMs: input.durationMs ?? 5_000,
    ...(input.body !== undefined ? { body: input.body } : {}),
  }
  state.toasts.push(toast)
  if (toast.durationMs > 0) {
    setTimeout(dismiss, toast.durationMs, toast.id)
  }
  return toast
}

function dismiss(id: number): void {
  const idx = state.toasts.findIndex((t) => t.id === id)
  if (idx >= 0) state.toasts.splice(idx, 1)
}

function clear(): void {
  state.toasts.splice(0, state.toasts.length)
}

export interface ToastApi {
  toasts: readonly Toast[]
  push: (input: ToastInput) => Toast
  success: (title: string, body?: string) => Toast
  error: (title: string, body?: string) => Toast
  info: (title: string, body?: string) => Toast
  dismiss: (id: number) => void
  clear: () => void
}

export function useToast(): ToastApi {
  return {
    toasts: readonly(state).toasts,
    push,
    success: (title: string, body?: string) =>
      push(body !== undefined ? { kind: 'success', title, body } : { kind: 'success', title }),
    error: (title: string, body?: string) =>
      push(body !== undefined ? { kind: 'error', title, body } : { kind: 'error', title }),
    info: (title: string, body?: string) =>
      push(body !== undefined ? { kind: 'info', title, body } : { kind: 'info', title }),
    dismiss,
    clear,
  }
}

/**
 * For test isolation: resets the singleton store + the id counter so
 * each `it()` starts from a clean slate. Not exported from the library
 * barrel — internal use only.
 */
export function _resetToastStateForTests(): void {
  state.toasts.splice(0, state.toasts.length)
  nextId = 1
}
