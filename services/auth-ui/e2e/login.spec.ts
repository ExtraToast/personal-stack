import { expect, test } from '@playwright/test'

test('login page renders', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('h1')).toContainText('Sign In')
})

test('login page renders username input', async ({ page }) => {
  await page.goto('/login')
  const usernameInput = page.locator('#username')
  await expect(usernameInput).toBeVisible()
  await expect(usernameInput).toHaveAttribute('type', 'text')
})

test('login page renders password input', async ({ page }) => {
  await page.goto('/login')
  const passwordInput = page.locator('#password')
  await expect(passwordInput).toBeVisible()
  await expect(passwordInput).toHaveAttribute('type', 'password')
})

test('login page renders submit button', async ({ page }) => {
  await page.goto('/login')
  const submitButton = page.getByRole('button', { name: /sign in/i })
  await expect(submitButton).toBeVisible()
})

test('login page has register link', async ({ page }) => {
  await page.goto('/login')
  const registerLink = page.getByRole('link', { name: /register/i })
  await expect(registerLink).toBeVisible()
})

test('register link navigates to register page', async ({ page }) => {
  await page.goto('/login')
  const registerLink = page.getByRole('link', { name: /register/i })
  await registerLink.click()
  await expect(page).toHaveURL(/\/register/)
})

test('register page renders all fields', async ({ page }) => {
  await page.goto('/register')
  await expect(page.locator('h1')).toContainText(/create account/i)
  await expect(page.locator('#username')).toBeVisible()
  await expect(page.locator('#email')).toBeVisible()
  await expect(page.locator('#password')).toBeVisible()
  await expect(page.locator('#confirmPassword')).toBeVisible()
  await expect(page.getByRole('button', { name: /create account/i })).toBeVisible()
})

test('register page has login link', async ({ page }) => {
  await page.goto('/register')
  const loginLink = page.getByRole('link', { name: /sign in/i })
  await expect(loginLink).toBeVisible()
})

test('login page preserves redirect query parameter', async ({ page }) => {
  await page.goto('/login?redirect=%2Ftotp-setup')
  await expect(page).toHaveURL(/redirect=%2Ftotp-setup/)
})

test('check-email page renders', async ({ page }) => {
  await page.goto('/check-email?email=test@example.com')
  await expect(page.locator('h1')).toContainText(/check your inbox/i)
  await expect(page.locator('body')).toContainText(/test@example.com/)
})

test('confirm-email page renders with token parameter', async ({ page }) => {
  await page.goto('/confirm-email?token=invalid-token')
  await expect(page.locator('h1')).toContainText(/confirm/i)
})
