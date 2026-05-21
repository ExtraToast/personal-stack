import type { FieldError, ProblemDetail } from '../types'
import { computed, ref, shallowRef } from 'vue'
import { ApiError } from '../types'

/**
 * Form-error binding for ProblemDetail-aware fetch helpers.
 *
 * The earlier UI pattern was `toast.errorFromCatch('Could not …', e)`
 * — every failure landed as a top-right toast, even when the server
 * already carried enough structure to anchor the error to the input
 * that caused it. The downside was twofold:
 *
 * 1. Users had to map the toast back to the form by eye.
 * 2. ProblemDetail extensions (`kubernetesReason`, `constraint`,
 *    `column`, `runnerStatus`, `retryAfterSeconds`, …) were dropped
 *    because `Error.message` only carries `detail`.
 *
 * `useFormErrors()` holds the last rejected [ApiError] so the form
 * can:
 *
 * render a top-of-form banner with the human-readable `detail` +
 *   structured extensions (via the `FormErrors` component or
 *   directly off `general.value`);
 * surface per-input errors next to the matching `FormField`
 *   (via `fieldErrorFor(column)`), keyed off the server's `column`
 *   extension on a 422 / the `errors[].field` list on a 400.
 *
 * The composable is intentionally side-effect-free: nothing
 * auto-clears, so the form decides when an error is stale (typically
 * at the next submit attempt). Toasts remain the right surface for
 * non-form failures (background refreshes, navigation loads), so this
 * composable is scoped to the submit path only.
 */
export interface UseFormErrorsReturn {
  /** Last rejected ApiError, or null when the form is clean. */
  readonly current: Readonly<{ value: ApiError | null }>
  /** The structured general-purpose banner content. Null when there's no error. */
  readonly general: Readonly<{ value: FormErrorBanner | null }>
  /** Look up the inline error for a specific column / field name. */
  fieldErrorFor: (field: string) => string | undefined
  /** Bind the rejection from a try / catch. Non-ApiError errors fall back to a generic banner. */
  captureFromCatch: (e: unknown) => void
  /** Discard the current error (typically called before a new submit). */
  clear: () => void
}

/**
 * The shape the top-of-form banner renders. Decoupled from
 * [ProblemDetail] so the banner template doesn't need to know about
 * the wire format.
 */
export interface FormErrorBanner {
  title: string
  /** Free-form message — `detail` if the server provided one, else the title. */
  detail: string
  /** Optional second-line annotation: "Postgres: workspaces_…_fkey", "Vault: 403 Forbidden", … */
  context?: string
  /** Suggested retry-after window when the server returned 503. */
  retryAfterSeconds?: number
  /** Server's machine-friendly runner status when the failure is a runner readiness issue. */
  runnerStatus?: string
}

export function useFormErrors(): UseFormErrorsReturn {
  const current = shallowRef<ApiError | null>(null)
  const fieldMap = ref<Map<string, string>>(new Map())

  const general = computed<FormErrorBanner | null>(() => {
    const e = current.value
    if (!e) return null
    const problem = e.problem
    return buildBanner(problem, e.message)
  })

  function fieldErrorFor(field: string): string | undefined {
    return fieldMap.value.get(field)
  }

  function captureFromCatch(e: unknown): void {
    if (isApiError(e)) {
      current.value = e
      fieldMap.value = collectFieldErrors(e.problem)
      return
    }
    // Non-ApiError (a thrown string, a TypeError, a foreign Error
    // shape): keep the toast-style detail on the banner so the user
    // still sees *something*; just no structured per-field anchor.
    const detail = e instanceof Error ? e.message : String(e)
    current.value = syntheticApiError(detail)
    fieldMap.value = new Map()
  }

  function clear(): void {
    current.value = null
    fieldMap.value = new Map()
  }

  return {
    current,
    general,
    fieldErrorFor,
    captureFromCatch,
    clear,
  }
}

function buildBanner(problem: ProblemDetail, fallback: string): FormErrorBanner {
  const banner: FormErrorBanner = {
    title: problem.title ?? 'Request failed',
    detail: problem.detail ?? fallback,
  }
  if (problem.runnerStatus) {
    banner.runnerStatus = problem.runnerStatus
  }
  if (typeof problem.retryAfterSeconds === 'number') {
    banner.retryAfterSeconds = problem.retryAfterSeconds
  }
  const context = buildContext(problem)
  if (context) banner.context = context
  return banner
}

function buildContext(problem: ProblemDetail): string | undefined {
  if (problem.kubernetesReason && problem.kubernetesCode) {
    return `Kubernetes ${problem.kubernetesCode} ${problem.kubernetesReason}`
  }
  if (problem.kubernetesReason) {
    return `Kubernetes ${problem.kubernetesReason}`
  }
  if (problem.constraint) {
    return `Postgres ${problem.constraint}`
  }
  if (problem.runnerStatus) {
    return `Runner ${problem.runnerStatus}`
  }
  return undefined
}

function collectFieldErrors(problem: ProblemDetail): Map<string, string> {
  const map = new Map<string, string>()
  // `column` is the single-field shortcut the server emits on a 422
  // constraint violation — it's the most specific anchor we have.
  if (problem.column && problem.detail) {
    map.set(problem.column, problem.detail)
  }
  // `errors[]` is the multi-field list emitted by validation errors
  // (jakarta.validation). When both are populated the `errors` entry
  // wins because it tends to carry a more user-readable message.
  for (const fe of problem.errors ?? []) {
    if (fe.field) {
      map.set(fe.field, fieldErrorMessage(fe))
    }
  }
  return map
}

function fieldErrorMessage(fe: FieldError): string {
  if (fe.rejectedValue !== undefined && fe.rejectedValue !== null && fe.rejectedValue !== '') {
    return `${fe.message} (got: ${String(fe.rejectedValue)})`
  }
  return fe.message
}

function isApiError(e: unknown): e is ApiError {
  return e instanceof Error && e.name === 'ApiError' && 'problem' in e
}

function syntheticApiError(detail: string): ApiError {
  return new ApiError({
    type: 'about:blank',
    title: 'Request failed',
    status: 0,
    detail,
  })
}
