import { beforeEach, describe, expect, it, vi } from 'vitest'
import { _resetToastStateForTests, formatProblemDetailBody, useToast } from '../composables/useToast'
import { ApiError } from '../types'

beforeEach(() => _resetToastStateForTests())

describe('useToast', () => {
  it('queues a toast with sensible defaults', () => {
    const toast = useToast()
    const t = toast.push({ title: 'Saved' })
    expect(t.id).toBe(1)
    expect(t.kind).toBe('info')
    expect(t.durationMs).toBe(5_000)
    expect(toast.toasts.length).toBe(1)
  })

  it('dismisses by id', () => {
    const toast = useToast()
    const a = toast.push({ title: 'A', durationMs: 0 })
    const b = toast.push({ title: 'B', durationMs: 0 })
    expect(toast.toasts.length).toBe(2)
    toast.dismiss(a.id)
    expect(toast.toasts.length).toBe(1)
    expect(toast.toasts[0]?.id).toBe(b.id)
  })

  it('auto-dismisses after durationMs', () => {
    vi.useFakeTimers()
    const toast = useToast()
    toast.success('Done')
    expect(toast.toasts.length).toBe(1)
    vi.advanceTimersByTime(5_000)
    expect(toast.toasts.length).toBe(0)
    vi.useRealTimers()
  })

  it('sticks when durationMs=0', () => {
    vi.useFakeTimers()
    const toast = useToast()
    toast.push({ title: 'Sticky', durationMs: 0 })
    vi.advanceTimersByTime(60_000)
    expect(toast.toasts.length).toBe(1)
    vi.useRealTimers()
  })

  it('clear() empties the queue', () => {
    const toast = useToast()
    toast.info('one')
    toast.info('two')
    toast.clear()
    expect(toast.toasts.length).toBe(0)
  })

  it('success/error/info helpers tag the kind correctly', () => {
    const toast = useToast()
    expect(toast.success('ok').kind).toBe('success')
    expect(toast.error('boom').kind).toBe('error')
    expect(toast.info('fyi').kind).toBe('info')
  })
})

describe('formatProblemDetailBody', () => {
  it('renders detail as the leading line', () => {
    const body = formatProblemDetailBody({
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'name must not be blank',
    })
    expect(body).toBe('name must not be blank')
  })

  it('falls back to title when detail is absent', () => {
    const body = formatProblemDetailBody({
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
    })
    expect(body).toBe('Bad Request')
  })

  it('surfaces the Kubernetes verdict and field errors on a 502', () => {
    const body = formatProblemDetailBody({
      type: 'https://jorisjonkers.dev/errors/kubernetes-api',
      title: 'Kubernetes API Error',
      status: 502,
      detail: 'Kubernetes API request failed (code=422, reason=Invalid): bad spec',
      kubernetesCode: 422,
      kubernetesReason: 'Invalid',
      errors: [
        { field: 'spec.image', message: 'must be set' },
        { field: 'spec.command', message: 'must not be empty' },
      ],
      traceId: 'abc-123',
    })
    expect(body).toBe(
      [
        'Kubernetes API request failed (code=422, reason=Invalid): bad spec',
        'Kubernetes: Invalid · code 422',
        '• spec.image: must be set',
        '• spec.command: must not be empty',
        'trace abc-123',
      ].join('\n'),
    )
  })

  it('omits empty sections for a vanilla payload', () => {
    const body = formatProblemDetailBody({
      type: 'about:blank',
      title: 'Not Found',
      status: 404,
      detail: 'session 123 does not exist',
    })
    expect(body).toBe('session 123 does not exist')
  })
})

describe('useToast.errorFromProblemDetail', () => {
  it('pushes an error toast with the formatted body', () => {
    const toast = useToast()
    toast.errorFromProblemDetail('Could not attach the deploy key', {
      type: 'about:blank',
      title: 'Bad Gateway',
      status: 502,
      detail: 'Vault is sealed',
      kubernetesReason: 'ServiceUnavailable',
    })
    expect(toast.toasts.length).toBe(1)
    expect(toast.toasts[0]).toMatchObject({
      kind: 'error',
      title: 'Could not attach the deploy key',
      body: 'Vault is sealed\nKubernetes: ServiceUnavailable',
    })
  })
})

describe('useToast.errorFromCatch', () => {
  it('uses the ProblemDetail body when the error is an ApiError', () => {
    const toast = useToast()
    toast.errorFromCatch(
      'Could not create',
      new ApiError({
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'duplicate name',
        errors: [{ field: 'name', message: 'already taken' }],
      }),
    )
    expect(toast.toasts[0]).toMatchObject({
      kind: 'error',
      title: 'Could not create',
      body: 'duplicate name\n• name: already taken',
    })
  })

  it('treats a plain ProblemDetail-shaped object as a ProblemDetail', () => {
    const toast = useToast()
    toast.errorFromCatch('Could not load', {
      type: 'about:blank',
      title: 'Bad Gateway',
      status: 502,
      detail: 'upstream timeout',
    })
    expect(toast.toasts[0]?.body).toBe('upstream timeout')
  })

  it('falls back to Error.message for plain Errors', () => {
    const toast = useToast()
    toast.errorFromCatch('Could not load', new Error('network down'))
    expect(toast.toasts[0]?.body).toBe('network down')
  })

  it('falls back to String(e) for non-Error values', () => {
    const toast = useToast()
    toast.errorFromCatch('Could not load', 'oops')
    expect(toast.toasts[0]?.body).toBe('oops')
  })
})
