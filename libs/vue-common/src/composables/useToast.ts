import type { ProblemDetail } from '../types'
import { reactive, readonly } from 'vue'
import { ApiError } from '../types'

export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  title: string
  body?: string
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

/**
 * Formats a [ProblemDetail] into a multi-line toast body. The leading
 * line is the human-readable `detail` (or `title` if `detail` is
 * absent); subsequent lines surface the Kubernetes API server's
 * verdict and any per-field validation errors. Empty sections are
 * omitted so a vanilla RFC 7807 payload still produces a clean
 * one-liner.
 */
export function formatProblemDetailBody(problem: ProblemDetail): string {
  const lines: string[] = []
  const headline = problem.detail ?? problem.title
  if (headline) lines.push(headline)
  if (problem.kubernetesReason || problem.kubernetesCode !== undefined) {
    const parts: string[] = []
    if (problem.kubernetesReason) parts.push(problem.kubernetesReason)
    if (problem.kubernetesCode !== undefined) parts.push(`code ${problem.kubernetesCode}`)
    lines.push(`Kubernetes: ${parts.join(' · ')}`)
  }
  if (problem.errors && problem.errors.length > 0) {
    for (const fe of problem.errors) {
      const label = fe.field ? `${fe.field}: ` : ''
      lines.push(`• ${label}${fe.message}`)
    }
  }
  if (problem.traceId) {
    lines.push(`trace ${problem.traceId}`)
  }
  return lines.join('\n')
}

/**
 * Convenience: same as `error(title, body)` but accepts a raw caught
 * value. If the value is an [ApiError] (or carries a ProblemDetail-
 * shaped payload), the body is built via [formatProblemDetailBody];
 * otherwise falls back to `e.message` / `String(e)` so legacy call
 * sites can drop their inline ternary without changing behaviour.
 */
function errorFromCatch(title: string, e: unknown): Toast {
  const problem = problemDetailOf(e)
  if (problem) {
    return push({ kind: 'error', title, body: formatProblemDetailBody(problem) })
  }
  const body = e instanceof Error ? e.message : String(e)
  return push({ kind: 'error', title, body })
}

function errorFromProblemDetail(title: string, problem: ProblemDetail): Toast {
  return push({ kind: 'error', title, body: formatProblemDetailBody(problem) })
}

function problemDetailOf(e: unknown): ProblemDetail | null {
  if (e instanceof ApiError) return e.problem
  // Defensive: an older call path (or a parallel module copy of the
  // ApiError class — common in monorepo build setups) might surface a
  // plain object whose shape matches ProblemDetail. Treat anything
  // with a numeric `status` + string `title` as one.
  if (isProblemDetailShape(e)) {
    return e
  }
  return null
}

function isProblemDetailShape(e: unknown): e is ProblemDetail {
  if (typeof e !== 'object' || e === null) return false
  const status = Reflect.get(e, 'status')
  const title = Reflect.get(e, 'title')
  return typeof status === 'number' && typeof title === 'string'
}

export interface ToastApi {
  toasts: readonly Toast[]
  push: (input: ToastInput) => Toast
  success: (title: string, body?: string) => Toast
  error: (title: string, body?: string) => Toast
  info: (title: string, body?: string) => Toast
  /**
   * Build an error toast from a [ProblemDetail], rendering the
   * Kubernetes verdict + field errors in a structured body.
   */
  errorFromProblemDetail: (title: string, problem: ProblemDetail) => Toast
  /**
   * Build an error toast from any caught value; picks the
   * ProblemDetail path when available, falls back to `e.message`.
   */
  errorFromCatch: (title: string, e: unknown) => Toast
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
    errorFromProblemDetail,
    errorFromCatch,
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
