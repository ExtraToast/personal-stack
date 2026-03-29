import { describe, expect, it } from 'vitest'
import { registerSchema } from '../schemas/registerSchema'

describe('registerSchema', () => {
  it('accepts valid registration data', () => {
    const result = registerSchema.safeParse({
      username: 'alice',
      email: 'alice@example.com',
      password: 'securepass123',
      confirmPassword: 'securepass123',
    })

    expect(result.success).toBe(true)
  })

  it('rejects mismatched passwords', () => {
    const result = registerSchema.safeParse({
      username: 'alice',
      email: 'alice@example.com',
      password: 'securepass123',
      confirmPassword: 'differentpass',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      const confirmError = result.error.issues.find((i) => i.path.includes('confirmPassword'))
      expect(confirmError).toBeDefined()
      expect(confirmError?.message).toBe('Passwords do not match')
    }
  })

  it('rejects invalid email', () => {
    const result = registerSchema.safeParse({
      username: 'alice',
      email: 'not-an-email',
      password: 'securepass123',
      confirmPassword: 'securepass123',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      const emailError = result.error.issues.find((i) => i.path.includes('email'))
      expect(emailError).toBeDefined()
    }
  })

  it('rejects short password', () => {
    const result = registerSchema.safeParse({
      username: 'alice',
      email: 'alice@example.com',
      password: 'short',
      confirmPassword: 'short',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      const passwordError = result.error.issues.find((i) => i.path.includes('password'))
      expect(passwordError).toBeDefined()
    }
  })
})
