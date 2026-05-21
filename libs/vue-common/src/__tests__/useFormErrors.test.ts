import { describe, expect, it } from 'vitest'
import { useFormErrors } from '../composables/useFormErrors'
import { ApiError } from '../types'

describe('useFormErrors', () => {
  it('captures an ApiError 422 with column + constraint and exposes both the banner and the field error', () => {
    // Mirrors the production failure on POST /api/v1/workspaces:
    // the workspace upsert violated `workspaces_github_link_id_fkey`,
    // and the new GlobalExceptionHandler now packages it as a 422 with
    // `column = "github_link_id"` so the UI can anchor the message.
    const form = useFormErrors()
    const err = new ApiError({
      type: 'https://jorisjonkers.dev/errors/constraint-violation',
      title: 'Constraint violation',
      status: 422,
      detail: 'Field `github_link_id` references a github_links row that does not exist',
      constraint: 'workspaces_github_link_id_fkey',
      column: 'github_link_id',
      referencedTable: 'github_links',
      errors: [
        {
          field: 'github_link_id',
          message: 'References a github_links row that does not exist',
        },
      ],
    })

    form.captureFromCatch(err)

    expect(form.current.value).toBe(err)
    expect(form.general.value).toMatchObject({
      title: 'Constraint violation',
      detail: 'Field `github_link_id` references a github_links row that does not exist',
      context: 'Postgres workspaces_github_link_id_fkey',
    })
    expect(form.fieldErrorFor('github_link_id')).toBe('References a github_links row that does not exist')
    expect(form.fieldErrorFor('name')).toBeUndefined()
  })

  it('captures an ApiError 503 from the agent-runner readiness path with runnerStatus + retryAfterSeconds', () => {
    // Mirrors PR S2: POST /api/v1/workspaces/{id}/sessions returns 503
    // when the runner Pod is not ready. The banner should render the
    // structured retry hint instead of a free-form context line.
    const form = useFormErrors()
    const err = new ApiError({
      type: 'https://jorisjonkers.dev/errors/agent-runner-unavailable',
      title: 'Agent runner not ready',
      status: 503,
      detail: 'Workspace abc runner is ConnectionRefused. Retry in 5s.',
      runnerStatus: 'ConnectionRefused',
      retryAfterSeconds: 5,
    })

    form.captureFromCatch(err)

    expect(form.general.value).toMatchObject({
      title: 'Agent runner not ready',
      runnerStatus: 'ConnectionRefused',
      retryAfterSeconds: 5,
    })
  })

  it('falls back to a synthetic banner when the error is not an ApiError', () => {
    const form = useFormErrors()

    form.captureFromCatch(new TypeError('Failed to fetch'))

    expect(form.general.value).toMatchObject({
      title: 'Request failed',
      detail: 'Failed to fetch',
    })
    expect(form.fieldErrorFor('anything')).toBeUndefined()
  })

  it('falls back to a synthetic banner when the catch handler receives a thrown string', () => {
    const form = useFormErrors()

    form.captureFromCatch('boom')

    expect(form.general.value?.detail).toBe('boom')
  })

  it('clear() drops the current error and the field map', () => {
    const form = useFormErrors()
    form.captureFromCatch(
      new ApiError({
        type: 'about:blank',
        title: 'Validation Error',
        status: 422,
        detail: 'One or more fields failed validation',
        errors: [{ field: 'name', message: 'must not be blank', rejectedValue: '' }],
      }),
    )
    expect(form.fieldErrorFor('name')).toContain('must not be blank')

    form.clear()

    expect(form.current.value).toBeNull()
    expect(form.general.value).toBeNull()
    expect(form.fieldErrorFor('name')).toBeUndefined()
  })

  it('errors[].rejectedValue is appended to the field message when present', () => {
    const form = useFormErrors()
    form.captureFromCatch(
      new ApiError({
        type: 'about:blank',
        title: 'Validation Error',
        status: 422,
        errors: [{ field: 'email', message: 'must be a valid email', rejectedValue: 'not-an-email' }],
      }),
    )

    expect(form.fieldErrorFor('email')).toBe('must be a valid email (got: not-an-email)')
  })

  it('errors[] entries take precedence over the column shortcut when both reference the same field', () => {
    // The server emits both `column` (the FK shortcut) and `errors[]`
    // (the multi-field list); the errors-entry message tends to be
    // more user-readable, so it wins on a key collision.
    const form = useFormErrors()
    form.captureFromCatch(
      new ApiError({
        type: 'about:blank',
        title: 'Constraint violation',
        status: 422,
        detail: 'Field `github_link_id` references a github_links row that does not exist',
        column: 'github_link_id',
        errors: [
          {
            field: 'github_link_id',
            message: 'The linked repository was removed — pick another',
          },
        ],
      }),
    )

    expect(form.fieldErrorFor('github_link_id')).toBe('The linked repository was removed — pick another')
  })
})
