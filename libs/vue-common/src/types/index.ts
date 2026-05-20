export interface User {
  id: string
  username: string
  email: string
  role: 'ADMIN' | 'USER' | 'READONLY'
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType?: string
}

/**
 * RFC 7807-flavoured problem payload shipped by every JVM service in
 * this stack (see `libs/kotlin-common/.../ProblemDetail.kt`). The base
 * fields are the standard; the trailing fields are extensions
 * specific to this stack — most importantly the upstream Kubernetes
 * API server's verdict on a 502 (so the UI can distinguish a 403 RBAC
 * denial from a 422 schema rejection without parsing free-form text).
 */
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  errors?: FieldError[]
  /** Correlation id from MDC, set by the upstream tracing filter. */
  traceId?: string
  /** Server-side exception class name — populated for unexpected 5xx. */
  exception?: string
  /** Kubernetes API server's numeric status code on a 502. */
  kubernetesCode?: number
  /**
   * Kubernetes API server's `Status.reason` on a 502 (e.g.
   * `Forbidden`, `Invalid`, `NotFound`).
   */
  kubernetesReason?: string
}

export interface FieldError {
  field: string
  message: string
  rejectedValue?: unknown
}

/**
 * Thrown by the API fetch helpers in this library when the server
 * returns a non-2xx with a ProblemDetail body. Extends `Error` so
 * existing `e instanceof Error ? e.message : String(e)` consumers
 * still pick up the human-readable `detail` string, while
 * ProblemDetail-aware call sites can drill into the structured
 * `problem` field via `e instanceof ApiError`.
 */
export class ApiError extends Error {
  public readonly problem: ProblemDetail
  public readonly status: number

  constructor(problem: ProblemDetail) {
    // Order of fallback matches what a human would naturally read in a
    // toast body: the structured `detail`, then the title, then a
    // generic last-resort string.
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.name = 'ApiError'
    this.problem = problem
    this.status = problem.status
  }
}
