import { expect, test } from '@playwright/test'

test('chat page renders', async ({ page }) => {
  await page.goto('/chat')
  await expect(page.locator('h1')).toContainText('Assistant')
})

test('chat page redirects unauthenticated users', async ({ page }) => {
  await page.goto('/chat')
  // Unauthenticated users should be redirected away from /chat
  const url = page.url()
  expect(url).toContain('/login')
})

test('chat page URL is /chat', async ({ page }) => {
  await page.goto('/')
  // Root should redirect to /chat
  await page.waitForURL('**/chat**')
  expect(page.url()).toContain('/chat')
})
