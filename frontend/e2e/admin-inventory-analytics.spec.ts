import { expect, test, type Page } from '@playwright/test'

/**
 * Headed/headless browser coverage for Admin Inventory + Analytics.
 * Requires: running stack on WEB_PORT (make deploy-local / up-all), seeded demo,
 * DEMO_ADMIN_EMAIL/PASSWORD and DEMO_CUSTOMER_EMAIL/PASSWORD in the environment.
 */

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`${name} is required for e2e (source repo .env via make e2e-inventory-analytics)`)
  }
  return value
}

async function loginAs(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel(/^email$/i).fill(email)
  await page.getByLabel(/^password$/i).fill(password)
  await page.getByRole('button', { name: /^sign in$/i }).click()
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/, { timeout: 20_000 })
}

test.describe('Admin inventory + analytics browser E2E', () => {
  const adminEmail = requireEnv('DEMO_ADMIN_EMAIL')
  const adminPassword = requireEnv('DEMO_ADMIN_PASSWORD')
  const customerEmail = requireEnv('DEMO_CUSTOMER_EMAIL')
  const customerPassword = requireEnv('DEMO_CUSTOMER_PASSWORD')

  test('customer is denied admin inventory', async ({ page }) => {
    await loginAs(page, customerEmail, customerPassword)
    await page.goto('/admin/inventory')
    await expect(page.getByRole('heading', { name: /access denied/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^inventory$/i })).toHaveCount(0)
  })

  test('admin inventory list, search/filter, and adjust sheet', async ({ page }) => {
    await loginAs(page, adminEmail, adminPassword)

    await page.goto('/admin/inventory')
    await expect(page.getByRole('heading', { name: /^inventory$/i })).toBeVisible()
    await expect(page.getByLabel(/^search$/i)).toBeVisible()

    // Summary strip from live inventory summary API (seeded FoodMart).
    await expect(page.getByLabel(/total products:/i)).toBeVisible()

    await page.getByLabel(/^search$/i).fill('FOOD')
    await page.getByLabel(/^stock status$/i).selectOption('LOW')
    await page.getByRole('button', { name: /^apply$/i }).click()

    await expect(page).toHaveURL(/stockStatus=LOW/)
    await expect(page).toHaveURL(/[?&]q=FOOD/)

    // Table or empty-state — either is valid for seeded LOW+FOOD; never invent rows.
    const table = page.locator('table')
    const empty = page.getByText(/no products match these inventory filters/i)
    await expect(table.or(empty)).toBeVisible()

    // Open absolute on-hand adjust UI from any visible row if present; otherwise clear
    // filters and open the first row (still uses live seed — no fixture fabrication).
    if (await table.count()) {
      const setOnHand = page.getByRole('button', { name: /^set on hand$/i }).first()
      if (await setOnHand.count()) {
        await setOnHand.click()
      } else {
        await page.getByLabel(/^stock status$/i).selectOption('ALL')
        await page.getByLabel(/^search$/i).fill('')
        await page.getByRole('button', { name: /^apply$/i }).click()
        await page.getByRole('button', { name: /^set on hand$/i }).first().click()
      }
    } else {
      await page.getByLabel(/^stock status$/i).selectOption('ALL')
      await page.getByLabel(/^search$/i).fill('')
      await page.getByRole('button', { name: /^apply$/i }).click()
      await page.getByRole('button', { name: /^set on hand$/i }).first().click()
    }

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByLabel(/quantity on hand/i)).toBeVisible()
    await expect(dialog.getByTestId('adjust-reserved-display')).toBeVisible()
    // Reserved is display-only (no editable control).
    await expect(dialog.locator('input[name="quantityReserved"]')).toHaveCount(0)
  })

  test('admin analytics dashboard, date-range Apply, and refresh', async ({ page }) => {
    await loginAs(page, adminEmail, adminPassword)

    await page.goto('/admin/analytics')
    await expect(page.getByRole('heading', { name: /^analytics$/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^overview$/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^inventory snapshot$/i })).toBeVisible()
    await expect(page.getByText(/view inventory/i)).toBeVisible()

    await page.getByLabel(/from \(utc day\)/i).fill('2026-01-01')
    await page.getByLabel(/through \(utc day/i).fill('2026-01-07')
    await page.getByRole('button', { name: /^apply$/i }).click()

    await expect(page).toHaveURL(/from=2026-01-01T00%3A00%3A00/)
    // Exclusive `to` Instant = day after inclusive end.
    await expect(page).toHaveURL(/to=2026-01-08T00%3A00%3A00/)

    await expect(page.getByRole('heading', { name: /orders.*revenue over time/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^order status$/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^top products$/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^category sales$/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^promotions$/i })).toBeVisible()

    const analyticsRequests: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/admin/analytics/')) {
        analyticsRequests.push(request.url())
      }
    })

    await page.getByRole('button', { name: /^refresh$/i }).click()
    await expect
      .poll(() => analyticsRequests.some((url) => url.includes('/admin/analytics/overview')))
      .toBe(true)
    await expect(page).toHaveURL(/from=2026-01-01T00%3A00%3A00/)
    await expect(page).toHaveURL(/to=2026-01-08T00%3A00%3A00/)
    await expect(page.getByRole('button', { name: /^refresh$/i })).toBeEnabled()

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('heading', { name: /^analytics$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /^refresh$/i })).toBeVisible()
  })
})
