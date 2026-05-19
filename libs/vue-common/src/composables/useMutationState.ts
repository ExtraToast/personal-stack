import type { ComputedRef, Ref } from 'vue'
import { computed, readonly, ref } from 'vue'

export type MutationStatus = 'idle' | 'pending' | 'success' | 'failure'

/**
 * State machine for a one-shot async submit:
 *
 *   idle → pending → success | failure → (auto-resets to idle after `resetDelayMs`)
 *
 * Wraps a single async action. The contract:
 *
 * - `pending` is true between `run()` being called and the wrapped promise
 *   settling. While pending, calling `run()` again is a no-op (returns the
 *   previous promise's result via `null`). This is exactly what every
 *   submit button needs — double-click is harmless.
 *
 * - On resolve, `status` flips to `success` and stays there for
 *   `resetDelayMs` so the UI can show a checkmark. Then it auto-resets to
 *   `idle`. The wrapped promise's resolved value is also returned from
 *   `run()` so callers can chain.
 *
 * - On reject, `status` flips to `failure`, `error` carries the thrown
 *   value, and `run()` re-throws so callers can `try/catch` if they want
 *   the original behaviour. The `failure` indicator stays visible for
 *   `resetDelayMs` and then auto-resets to `idle`.
 *
 * - `reset()` is a manual override — drops back to `idle` and clears any
 *   stored error. Useful when the same button is used across distinct
 *   submission contexts.
 *
 * The reset timer is cleared on unmount-equivalent (a follow-up `run()`),
 * so a fast double-submit doesn't leave a stale timer fighting the new
 * status.
 */
export interface UseMutationStateOptions {
  /**
   * How long the `success` or `failure` indicator stays before
   * auto-resetting back to `idle`. Default 2_000 ms.
   */
  resetDelayMs?: number
}

export interface MutationState<T = unknown> {
  status: Readonly<Ref<MutationStatus>>
  pending: ComputedRef<boolean>
  success: ComputedRef<boolean>
  failure: ComputedRef<boolean>
  error: Readonly<Ref<unknown>>
  /**
   * Run the action. Returns the action's resolved value, or `null` if
   * a previous run was still pending (in which case the call was a
   * no-op). Re-throws on rejection so callers can catch if they want
   * to handle the error themselves; the state machine also records the
   * error and surfaces it via `failure` / `error`.
   */
  run: (action: () => Promise<T>) => Promise<T | null>
  reset: () => void
}

export function useMutationState<T = unknown>(options: UseMutationStateOptions = {}): MutationState<T> {
  const resetDelayMs = options.resetDelayMs ?? 2_000
  const status = ref<MutationStatus>('idle')
  const error = ref<unknown>(null)
  let resetTimer: ReturnType<typeof setTimeout> | null = null

  function clearTimer(): void {
    if (resetTimer !== null) {
      clearTimeout(resetTimer)
      resetTimer = null
    }
  }

  function scheduleReset(): void {
    clearTimer()
    if (resetDelayMs > 0) {
      resetTimer = setTimeout(() => {
        status.value = 'idle'
        error.value = null
        resetTimer = null
      }, resetDelayMs)
    }
  }

  async function run(action: () => Promise<T>): Promise<T | null> {
    if (status.value === 'pending') return null
    clearTimer()
    status.value = 'pending'
    error.value = null
    try {
      const result = await action()
      status.value = 'success'
      scheduleReset()
      return result
    } catch (e) {
      status.value = 'failure'
      error.value = e
      scheduleReset()
      throw e
    }
  }

  function reset(): void {
    clearTimer()
    status.value = 'idle'
    error.value = null
  }

  return {
    status: readonly(status),
    pending: computed(() => status.value === 'pending'),
    success: computed(() => status.value === 'success'),
    failure: computed(() => status.value === 'failure'),
    error: readonly(error),
    run,
    reset,
  }
}
