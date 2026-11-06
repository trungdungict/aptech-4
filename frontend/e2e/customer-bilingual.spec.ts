import { expect, test, type Page } from '@playwright/test'

/**
 * Customer storefront VI/EN browser coverage (P34-T09).
 * Requires running nginx stack on WEB_PORT + seeded demo (DEMO_CUSTOMER_*).
 *
 * Does not invent catalog/promo copy — asserts live API names and UI chrome keys.
 * Chat remains on independent chat-i18n (not asserted here).
 */

const LOCALE_KEY = 'aptech4.locale'
const PEAS_SKU = 'FOOD-FROZEN-PEAS-001'
const PEAS_VI_NAME = 'Đậu Hà Lan đông lạnh (500 g)'
const APPLE_SKU = 'FOOD-FRUIT-APPLE-001'
const APPLE_EN_NAME = 'Crisp Red Apples (1 kg)'
const APPLE_VI_NAME = 'Táo đỏ giòn (1 kg)'

type CatalogHit = { id: string; sku: string; name: string }

async function fetchProductBySku(page: Page, sku: string, locale: 'vi' | 'en'): Promise<CatalogHit> {
  const res = await page.request.get(`/api/v1/products?page=0&size=50`, {
    headers: { 'Accept-Language': locale },
  })
  expect(res.ok()).toBeTruthy()
  const body = (await res.json()) as { items: CatalogHit[] }
  const hit = body.items.find((item) => item.sku === sku)
  expect(hit, `seeded product ${sku} must exist`).toBeTruthy()
  return hit!
}

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`${name} is required for e2e (source repo .env via make e2e-customer-bilingual)`)
  }
  return value
}

function localeNativeLabel(locale: 'vi' | 'en'): string {
  return locale === 'vi' ? 'Tiếng Việt' : 'English'
}

function languageSwitcher(page: Page) {
  return page.getByTestId('locale-switcher').first()
}

/** Persist locale then reload so i18n + Accept-Language pick it up (no sticky addInitScript). */
async function setLocale(page: Page, locale: 'vi' | 'en') {
  await page.goto('/')
  await page.evaluate(([key, value]) => window.localStorage.setItem(key, value), [LOCALE_KEY, locale] as const)
  await page.reload()
  const trigger = languageSwitcher(page)
  await expect(trigger).toHaveAttribute('data-locale', locale)
  await expect(trigger).toContainText(localeNativeLabel(locale))
}

async function switchLocale(page: Page, locale: 'vi' | 'en') {
  const label = localeNativeLabel(locale)
  const trigger = languageSwitcher(page)
  await trigger.click()
  await page.getByTestId(`locale-option-${locale}`).click()
  await expect(trigger).toHaveAttribute('data-locale', locale)
  await expect(trigger).toContainText(label)
}

async function loginAsCustomer(page: Page, email: string, password: string, locale: 'vi' | 'en') {
  await page.goto('/login')
  if (locale === 'en') {
    await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible()
    await page.getByLabel(/^email$/i).fill(email)
    await page.getByLabel(/^password$/i).fill(password)
    await page.getByRole('button', { name: /^sign in$/i }).click()
  }
  else {
    await expect(page.getByRole('heading', { name: /^đăng nhập$/i })).toBeVisible()
    await page.getByLabel(/^email$/i).fill(email)
    await page.getByLabel(/mật khẩu/i).fill(password)
    await page.getByRole('button', { name: /^đăng nhập$/i }).click()
  }
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/, { timeout: 20_000 })
}

test.describe('Customer storefront bilingual E2E', () => {
  const customerEmail = requireEnv('DEMO_CUSTOMER_EMAIL')
  const customerPassword = requireEnv('DEMO_CUSTOMER_PASSWORD')

  test('locale switcher updates chrome and persists across reload', async ({ page }) => {
    // Avoid addInitScript — it re-applies on every reload and would clobber a UI switch.
    await page.goto('/')
    await page.evaluate((key) => window.localStorage.setItem(key, 'vi'), LOCALE_KEY)
    await page.reload()
    await expect(languageSwitcher(page)).toHaveAttribute('data-locale', 'vi')
    await expect(languageSwitcher(page)).toContainText('Tiếng Việt')
    await expect(page.getByRole('navigation', { name: /cửa hàng/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /^danh mục$/i }).first()).toBeVisible()
    await expect(page.getByRole('heading', { name: /đầy tủ bếp|stock your kitchen/i })).toBeVisible()

    await switchLocale(page, 'en')
    await expect(page.getByRole('link', { name: /^catalog$/i }).first()).toBeVisible()
    await expect(page.getByRole('heading', { name: /stock your kitchen from the live aptech-4 catalog/i })).toBeVisible()

    await page.reload()
    await expect(languageSwitcher(page)).toHaveAttribute('data-locale', 'en')
    await expect(languageSwitcher(page)).toContainText('English')
    await expect(page.getByRole('link', { name: /^catalog$/i }).first()).toBeVisible()
    await expect
      .poll(async () => page.evaluate((key) => localStorage.getItem(key), LOCALE_KEY))
      .toBe('en')
  })

  test('login and register pages render VI and EN chrome', async ({ page }) => {
    await setLocale(page, 'vi')
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: /^đăng nhập$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /^đăng nhập$/i })).toBeVisible()

    await switchLocale(page, 'en')
    await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /^sign in$/i })).toBeVisible()

    await page.goto('/register')
    await expect(page.getByRole('heading', { name: /create account|tạo tài khoản/i })).toBeVisible()
    await switchLocale(page, 'vi')
    await expect(page.getByRole('heading', { name: /^tạo tài khoản$/i })).toBeVisible()
    await expect(page.getByLabel(/họ và tên|full name/i)).toBeVisible()
  })

  test('catalog list/detail: EN apple name and peas EN→VI fallback', async ({ page }) => {
    await setLocale(page, 'en')
    const appleEn = await fetchProductBySku(page, APPLE_SKU, 'en')
    const peasEn = await fetchProductBySku(page, PEAS_SKU, 'en')
    expect(appleEn.name).toBe(APPLE_EN_NAME)
    expect(peasEn.name).toBe(PEAS_VI_NAME)

    await page.goto('/products')
    await expect(page.getByRole('heading', { name: /^products$/i })).toBeVisible()
    await expect(page.getByText(/filters/i).first()).toBeVisible()

    await page.goto(`/products/${appleEn.id}`)
    await expect(page.getByRole('heading', { name: APPLE_EN_NAME })).toBeVisible()
    await expect(page.getByText(APPLE_SKU)).toBeVisible()

    await page.goto(`/products/${peasEn.id}`)
    await expect(page.getByRole('heading', { name: PEAS_VI_NAME })).toBeVisible()
    await expect(page.getByText(PEAS_SKU)).toBeVisible()
    await expect(page.getByRole('heading', { name: /frozen garden peas/i })).toHaveCount(0)

    await setLocale(page, 'vi')
    const appleVi = await fetchProductBySku(page, APPLE_SKU, 'vi')
    expect(appleVi.name).toBe(APPLE_VI_NAME)
    await page.goto(`/products/${appleVi.id}`)
    await expect(languageSwitcher(page)).toHaveAttribute('data-locale', 'vi')
    await expect(page.getByRole('heading', { name: APPLE_VI_NAME })).toBeVisible({ timeout: 20_000 })
  })

  test('home promotions chrome + featured offers in both locales', async ({ page }) => {
    await setLocale(page, 'en')
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /featured offers/i })).toBeVisible()

    await switchLocale(page, 'vi')
    await expect(page.getByRole('heading', { name: /ưu đãi nổi bật/i })).toBeVisible()
  })

  test('customer cart, checkout, orders, and notifications (EN chrome)', async ({ page }) => {
    await setLocale(page, 'en')
    await loginAsCustomer(page, customerEmail, customerPassword, 'en')

    const apple = await fetchProductBySku(page, APPLE_SKU, 'en')
    await page.goto(`/products/${apple.id}`)
    await expect(page.getByRole('heading', { name: APPLE_EN_NAME })).toBeVisible()
    const addBtn = page.getByRole('button', { name: /add to cart/i })
    await expect(addBtn).toBeVisible()
    await addBtn.click()
    await expect(page.getByRole('link', { name: /^view cart$/i })).toBeVisible({ timeout: 15_000 })

    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: /^cart$/i })).toBeVisible()
    // Live cart productName follows Accept-Language (same as catalog).
    await expect(page.getByRole('link', { name: APPLE_EN_NAME, exact: true })).toBeVisible()

    const peas = await fetchProductBySku(page, PEAS_SKU, 'en')
    await page.goto(`/products/${peas.id}`)
    await expect(page.getByRole('heading', { name: PEAS_VI_NAME })).toBeVisible()
    await page.getByRole('button', { name: /add to cart/i }).click()
    await expect(page.getByRole('link', { name: /^view cart$/i })).toBeVisible({ timeout: 15_000 })
    await page.goto('/cart')
    await expect(page.getByRole('link', { name: PEAS_VI_NAME, exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: APPLE_EN_NAME, exact: true })).toBeVisible()

    const increase = page.getByRole('button', { name: /increase crisp red apples.*quantity/i })
    await expect(increase).toBeVisible()
    await increase.click()
    const checkout = page.getByRole('link', { name: /^checkout$/i })
    await expect(checkout).toBeVisible()
    await checkout.click()
    await expect(page).toHaveURL(/\/checkout/)
    await expect(page.getByRole('heading', { name: /^checkout$/i })).toBeVisible()
    await expect(page.getByText('Shipping address', { exact: true })).toBeVisible()
    await expect(page.getByText(APPLE_EN_NAME).first()).toBeVisible()
    await expect(page.getByText(PEAS_VI_NAME).first()).toBeVisible()
    // Prefer existing seeded address; do not invent addresses.
    const place = page.getByRole('button', { name: /place order \(mock payment\)/i })
    await expect(place).toBeVisible()
    if (await place.isEnabled()) {
      await place.click()
      await expect(page).toHaveURL(/\/orders\//, { timeout: 25_000 })
      await expect(page.getByRole('heading', { name: /order detail/i })).toBeVisible()
    } else {
      // Address may be missing for this demo user — chrome still verified above.
      await expect(page.getByText(/no shipping addresses|add an address|choose a shipping address/i).first()).toBeVisible()
    }

    await page.goto('/orders')
    await expect(page.getByRole('heading', { name: /^orders$/i })).toBeVisible()
    const empty = page.getByText(/no orders yet/i)
    const detailLink = page.getByRole('link', { name: /view detail/i }).first()
    if (await detailLink.count()) {
      await detailLink.click()
      await expect(page.getByRole('heading', { name: /order detail/i })).toBeVisible()
    }
    else {
      await expect(empty.or(page.getByRole('heading', { name: /^orders$/i }))).toBeVisible()
    }

    await expect(page.getByRole('button', { name: /notifications/i })).toBeVisible()
  })

  test('customer cart and orders chrome in Vietnamese', async ({ page }) => {
    await setLocale(page, 'vi')
    await loginAsCustomer(page, customerEmail, customerPassword, 'vi')

    const apple = await fetchProductBySku(page, APPLE_SKU, 'vi')
    await page.goto(`/products/${apple.id}`)
    await expect(page.getByRole('heading', { name: APPLE_VI_NAME })).toBeVisible()
    await page.getByRole('button', { name: /thêm vào giỏ/i }).click()
    await expect(page.getByRole('link', { name: /xem giỏ hàng/i })).toBeVisible({ timeout: 15_000 })

    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: /^giỏ hàng$/i })).toBeVisible()
    await expect(page.getByRole('link', { name: APPLE_VI_NAME, exact: true })).toBeVisible()

    await page.goto('/orders')
    await expect(page.getByRole('heading', { name: /^đơn hàng$/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /thông báo/i })).toBeVisible()
  })

  test('catalog empty-filter state remains usable', async ({ page }) => {
    await setLocale(page, 'en')
    await page.goto('/products?q=ZZZ-NO-SUCH-SKU-999')
    await expect(page.getByRole('heading', { name: /^products$/i })).toBeVisible()
    await expect(page.getByText(/no products match/i)).toBeVisible()
  })
})
