import { describe, expect, it } from 'vitest'
import { loginSchema } from '../schemas/loginSchema'

describe('loginSchema', () => {
  it('accepts valid login data', () => {
    const result = loginSchema.safeParse({
      username: 'alice',
      password: 'secret123',
    })

    expect(result.success).toBe(true)
  })

  it('rejects empty username', () => {
    const result = loginSchema.safeParse({
      username: '',
      password: 'secret123',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      const usernameError = result.error.issues.find((i) => i.path.includes('username'))
      expect(usernameError).toBeDefined()
    }
  })

  it('rejects empty password', () => {
    const result = loginSchema.safeParse({
      username: 'alice',
      password: '',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      const passwordError = result.error.issues.find((i) => i.path.includes('password'))
      expect(passwordError).toBeDefined()
    }
  })
})
