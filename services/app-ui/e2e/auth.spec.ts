import { expect, test } from '@playwright/test'

test('login button is visible when not authenticated', async ({ page }) => {
  await page.goto('/')
  const loginButton = page.getByRole('button', { name: /login|inloggen/i })
  await expect(loginButton).toBeVisible()
})

test('callback page shows loading state', async ({ page }) => {
  await page.goto('/callback')
  // Without a valid code/state, the page should show an error or loading message
  await expect(page.locator('body')).toContainText(/sign-in|Authentication failed|Invalid callback/i)
})
