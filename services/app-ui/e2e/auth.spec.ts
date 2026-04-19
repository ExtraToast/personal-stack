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

test('home page renders hero section', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('h1')).toContainText(/joris jonkers/i)
})

test('callback page shows error for invalid error param', async ({ page }) => {
  await page.goto('/callback?error=access_denied')
  await expect(page.locator('body')).toContainText(/authentication failed/i)
})

test('apps page requires authentication', async ({ page }) => {
  await page.goto('/apps')
  // Unauthenticated users should be redirected to home
  await expect(page).toHaveURL('/')
})

test('navigation links are visible', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: /about/i })).toBeVisible()
  await expect(page.getByRole('link', { name: /experience/i })).toBeVisible()
  await expect(page.getByRole('link', { name: /projects/i })).toBeVisible()
  await expect(page.getByRole('link', { name: /skills/i })).toBeVisible()
  await expect(page.getByRole('link', { name: /contact/i })).toBeVisible()
})

test('theme toggle button is present', async ({ page }) => {
  await page.goto('/')
  const themeButton = page.getByRole('button', { name: /light|dark|system/i })
  await expect(themeButton).toBeVisible()
})
