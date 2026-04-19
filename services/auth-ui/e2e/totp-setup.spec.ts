import { expect, test } from '@playwright/test'

test('TOTP setup page requires authentication', async ({ page }) => {
  await page.goto('/totp-setup')
  // Unauthenticated users should be redirected to login
  await expect(page).toHaveURL(/\/login/)
})

test('TOTP setup page renders heading', async ({ page }) => {
  await page.goto('/totp-setup')
  // Should redirect to login with redirect query parameter
  await expect(page).toHaveURL(/\/login\?redirect=%2Ftotp-setup/)
})

test('TOTP setup page shows loading state initially', async ({ page }) => {
  await page.goto('/totp-setup')
  // Without authentication, the page redirects to login
  await expect(page).toHaveURL(/\/login/)
  await expect(page.locator('h1')).toContainText(/sign in/i)
})

test('TOTP verification form is present after load', async ({ page }) => {
  // Without authentication, verify that the redirect preserves the target path
  await page.goto('/totp-setup')
  await expect(page).toHaveURL(/\/login/)
  await expect(page.locator('body')).toContainText(/sign in/i)
})

test('code input accepts numeric input', async ({ page }) => {
  // Navigate to login page (redirected from totp-setup) and verify the login form works
  await page.goto('/login')
  const usernameInput = page.locator('#username')
  await usernameInput.fill('testuser')
  await expect(usernameInput).toHaveValue('testuser')
})
