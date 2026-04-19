import { describe, expect, it } from 'vitest'
import { z } from 'zod'

// TOTP schema matching backend TotpVerifyRequest validation:
// @NotBlank + @Pattern(regexp = "^\d{6}$")
const totpSchema = z.object({
  code: z
    .string()
    .min(1, 'TOTP code is required')
    .regex(/^\d{6}$/, 'TOTP code must be 6 digits'),
})

describe('totpSchema', () => {
  it('accepts valid 6-digit code', () => {
    const result = totpSchema.safeParse({ code: '123456' })

    expect(result.success).toBe(true)
  })

  it('rejects code shorter than 6 digits', () => {
    const result = totpSchema.safeParse({ code: '123' })

    expect(result.success).toBe(false)
    if (!result.success) {
      const codeError = result.error.issues.find((i) => i.path.includes('code'))
      expect(codeError).toBeDefined()
    }
  })

  it('rejects non-numeric code', () => {
    const result = totpSchema.safeParse({ code: 'abcdef' })

    expect(result.success).toBe(false)
    if (!result.success) {
      const codeError = result.error.issues.find((i) => i.path.includes('code'))
      expect(codeError).toBeDefined()
      expect(codeError?.message).toBe('TOTP code must be 6 digits')
    }
  })
})
