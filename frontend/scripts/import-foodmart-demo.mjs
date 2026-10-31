#!/usr/bin/env node
/**
 * Idempotent FoodMart grocery demo import via existing Admin APIs.
 *
 * Default mode: dry-run (no writes).
 * Apply mode:   --apply
 *
 * Env (explicit shell exports override frontend/.env / .env.local):
 *   DEMO_ADMIN_EMAIL
 *   DEMO_ADMIN_PASSWORD
 *   API_BASE_URL  (default http://localhost:8088/api/v1)
 *
 * Never deletes records. Never logs passwords or tokens.
 */

import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const DEMO_DATA_PATH = join(
  FRONTEND_ROOT,
  'src/features/catalog/data/foodmart-demo-data.json',
)
const IMAGE_MAP_PATH = join(
  FRONTEND_ROOT,
  'src/features/catalog/data/foodmart-image-map.json',
)
const PUBLIC_ROOT = join(FRONTEND_ROOT, 'public')

const APPLY = process.argv.includes('--apply')
const DRY_RUN = !APPLY

function fail(message) {
  console.error(`ERROR: ${message}`)
  process.exit(1)
}

/**
 * Minimal .env loader (no dotenv dependency).
 * Does not override keys already present in process.env.
 */
function parseEnvFile(content) {
  const out = {}
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) continue
    const eq = line.indexOf('=')
    if (eq <= 0) continue
    const key = line.slice(0, eq).trim()
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue
    let value = line.slice(eq + 1).trim()
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1)
    }
    out[key] = value
  }
  return out
}

function loadProjectEnv() {
  const loaded = []
  // Later files fill only unset keys; process.env always wins over files.
  for (const name of ['.env', '.env.local']) {
    const path = join(FRONTEND_ROOT, name)
    if (!existsSync(path)) continue
    const parsed = parseEnvFile(readFileSync(path, 'utf8'))
    let applied = 0
    for (const [key, value] of Object.entries(parsed)) {
      if (process.env[key] === undefined) {
        process.env[key] = value
        applied += 1
      }
    }
    loaded.push(`${name}(${applied})`)
  }
  return loaded
}

function loadJson(path) {
  return JSON.parse(readFileSync(path, 'utf8'))
}

function validateDataset(demo, imageMap) {
  const errors = []
  const categoryKeys = new Set()
  const skus = new Set()

  if (!Array.isArray(demo.categories) || demo.categories.length === 0) {
    errors.push('categories must be a non-empty array')
  }
  if (!Array.isArray(demo.products) || demo.products.length === 0) {
    errors.push('products must be a non-empty array')
  }

  for (const cat of demo.categories ?? []) {
    if (!cat.key || !cat.name) errors.push(`category missing key/name: ${JSON.stringify(cat)}`)
    if (categoryKeys.has(cat.key)) errors.push(`duplicate category key: ${cat.key}`)
    categoryKeys.add(cat.key)
    if (String(cat.name).length > 120) errors.push(`category name too long: ${cat.name}`)
    if (cat.description && String(cat.description).length > 4000) {
      errors.push(`category description too long: ${cat.key}`)
    }
  }

  let mapped = 0
  let missingMap = 0
  let missingFile = 0

  for (const product of demo.products ?? []) {
    if (!product.sku || !product.name || !product.categoryKey) {
      errors.push(`product incomplete: ${JSON.stringify(product)}`)
      continue
    }
    if (skus.has(product.sku.toUpperCase())) errors.push(`duplicate SKU: ${product.sku}`)
    skus.add(product.sku.toUpperCase())
    if (!categoryKeys.has(product.categoryKey)) {
      errors.push(`product ${product.sku} references unknown categoryKey ${product.categoryKey}`)
    }
    if (String(product.sku).length > 64) errors.push(`SKU too long: ${product.sku}`)
    if (String(product.name).length > 200) errors.push(`name too long: ${product.sku}`)
    if (product.description && String(product.description).length > 10_000) {
      errors.push(`description too long: ${product.sku}`)
    }
    if (typeof product.unitPrice !== 'number' || !(product.unitPrice >= 0)) {
      errors.push(`invalid unitPrice for ${product.sku}`)
    }

    const mapPath = imageMap[product.sku] || product.imagePath
    if (!mapPath || mapPath.startsWith('_')) {
      missingMap += 1
      errors.push(`no image mapping for ${product.sku}`)
    } else {
      mapped += 1
      const diskPath = join(PUBLIC_ROOT, mapPath.replace(/^\//, ''))
      if (!existsSync(diskPath)) {
        missingFile += 1
        errors.push(`image file missing for ${product.sku}: ${mapPath}`)
      }
      if (product.imagePath && product.imagePath !== mapPath) {
        errors.push(
          `imagePath mismatch for ${product.sku}: demo=${product.imagePath} map=${mapPath}`,
        )
      }
    }
  }

  return {
    errors,
    stats: {
      categories: demo.categories?.length ?? 0,
      products: demo.products?.length ?? 0,
      uniqueSkus: skus.size,
      imagesMapped: mapped,
      imagesMissingMap: missingMap,
      imagesMissingFile: missingFile,
    },
  }
}

/** Set in main() after env load. */
let apiBase = 'http://localhost:8088/api/v1'

async function api(pathname, { method = 'GET', token, body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${apiBase}${pathname}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { raw: text.slice(0, 200) }
    }
  }

  if (!response.ok) {
    const message = data?.message || data?.error || response.statusText
    const err = new Error(`${method} ${pathname} → ${response.status}: ${message}`)
    err.status = response.status
    err.data = data
    throw err
  }
  return data
}

async function login(email, password) {
  const data = await api('/auth/login', {
    method: 'POST',
    body: { email, password },
  })
  if (!data?.accessToken) fail('Login response missing accessToken')
  const roles = data.user?.roles ?? []
  if (!roles.includes('ADMIN')) fail('Authenticated user is not ADMIN')
  return data.accessToken
}

async function listAllAdminProducts(token) {
  const items = []
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const data = await api(`/admin/products?page=${page}&size=100&sort=name,asc`, { token })
    items.push(...(data.items ?? []))
    totalPages = Math.max(1, data.totalPages ?? 1)
    page += 1
    if (page > 200) fail('Admin product paging exceeded safety limit')
  }
  return items
}

function moneyEqual(a, b) {
  return Number(a).toFixed(2) === Number(b).toFixed(2)
}

function productPayloadEqual(existing, desired, categoryId) {
  return (
    existing.sku?.toLowerCase() === desired.sku.toLowerCase() &&
    existing.name === desired.name &&
    (existing.description ?? null) === (desired.description ?? null) &&
    moneyEqual(existing.unitPrice, desired.unitPrice) &&
    (existing.currency || 'VND') === (desired.currency || 'VND') &&
    existing.categoryId === categoryId
  )
}

function summarizeCounters(counters) {
  return Object.entries(counters)
    .map(([k, v]) => `${k}=${v}`)
    .join(' ')
}

async function main() {
  const loadedEnv = loadProjectEnv()
  apiBase = (process.env.API_BASE_URL || 'http://localhost:8088/api/v1').replace(/\/$/, '')

  const email = process.env.DEMO_ADMIN_EMAIL
  const password = process.env.DEMO_ADMIN_PASSWORD
  if (!email || !password) {
    fail(
      'Missing DEMO_ADMIN_EMAIL / DEMO_ADMIN_PASSWORD. Add them to frontend/.env (see .env.example) or export them. Values are never logged.',
    )
  }

  const demo = loadJson(DEMO_DATA_PATH)
  const imageMapRaw = loadJson(IMAGE_MAP_PATH)
  const imageMap = Object.fromEntries(
    Object.entries(imageMapRaw).filter(([k]) => !k.startsWith('_')),
  )

  const validation = validateDataset(demo, imageMap)
  console.log('=== FoodMart demo import ===')
  console.log(`Mode: ${DRY_RUN ? 'DRY-RUN (no writes)' : 'APPLY (will write)'}`)
  console.log(
    `Env files: ${loadedEnv.length ? loadedEnv.join(', ') : 'none'} (explicit env overrides files)`,
  )
  console.log(`API: ${apiBase}`)
  console.log(`Admin email: ${email}`)
  console.log(
    `Dataset: categories=${validation.stats.categories} products=${validation.stats.products} imagesMapped=${validation.stats.imagesMapped}`,
  )

  if (validation.errors.length) {
    console.error('Dataset validation failed:')
    for (const err of validation.errors.slice(0, 40)) console.error(`  - ${err}`)
    if (validation.errors.length > 40) {
      console.error(`  … ${validation.errors.length - 40} more`)
    }
    process.exit(1)
  }

  const token = await login(email, password)
  console.log('Auth: OK (ADMIN)')

  const existingCategories = await api('/admin/categories', { token })
  const existingProducts = await listAllAdminProducts(token)

  const categoryByName = new Map(
    existingCategories.map((c) => [String(c.name).trim().toLowerCase(), c]),
  )
  const productBySku = new Map(
    existingProducts.map((p) => [String(p.sku).trim().toLowerCase(), p]),
  )

  const foodSkusInDb = [...productBySku.keys()].filter((s) => s.startsWith('food-'))
  const conflictingFoodSkus = demo.products
    .map((p) => p.sku)
    .filter((sku) => productBySku.has(sku.toLowerCase()))

  const unrelatedExisting = existingProducts.filter(
    (p) => !String(p.sku).toUpperCase().startsWith('FOOD-'),
  )

  console.log(`Existing admin categories: ${existingCategories.length}`)
  console.log(`Existing admin products: ${existingProducts.length}`)
  console.log(`Existing FOOD-* SKUs: ${foodSkusInDb.length}`)
  console.log(`Unrelated (non-FOOD-*) products left untouched: ${unrelatedExisting.length}`)
  console.log(`Dataset SKUs already present: ${conflictingFoodSkus.length}`)

  const categoryCounters = { create: 0, skip: 0, update: 0, fail: 0 }
  const productCounters = { create: 0, skip: 0, update: 0, fail: 0 }
  const planned = []
  const categoryIdByKey = new Map()

  for (const cat of demo.categories) {
    const existing = categoryByName.get(cat.name.trim().toLowerCase())
    if (existing) {
      categoryIdByKey.set(cat.key, existing.id)
      const descSame = (existing.description ?? null) === (cat.description ?? null)
      if (descSame) {
        categoryCounters.skip += 1
        planned.push({ type: 'category', action: 'skip', name: cat.name, id: existing.id })
      } else {
        categoryCounters.update += 1
        planned.push({
          type: 'category',
          action: DRY_RUN ? 'would-update' : 'update',
          name: cat.name,
          id: existing.id,
        })
        if (!DRY_RUN) {
          try {
            const saved = await api(`/admin/categories/${existing.id}`, {
              method: 'PUT',
              token,
              body: {
                name: cat.name,
                description: cat.description ?? null,
                parentId: null,
              },
            })
            categoryIdByKey.set(cat.key, saved.id)
            categoryByName.set(cat.name.trim().toLowerCase(), saved)
          } catch (err) {
            categoryCounters.update -= 1
            categoryCounters.fail += 1
            planned.push({ type: 'category', action: 'fail', name: cat.name, error: err.message })
          }
        }
      }
      continue
    }

    categoryCounters.create += 1
    planned.push({
      type: 'category',
      action: DRY_RUN ? 'would-create' : 'create',
      name: cat.name,
    })
    if (!DRY_RUN) {
      try {
        const saved = await api('/admin/categories', {
          method: 'POST',
          token,
          body: {
            name: cat.name,
            description: cat.description ?? null,
            parentId: null,
          },
        })
        categoryIdByKey.set(cat.key, saved.id)
        categoryByName.set(cat.name.trim().toLowerCase(), saved)
      } catch (err) {
        categoryCounters.create -= 1
        categoryCounters.fail += 1
        planned.push({ type: 'category', action: 'fail', name: cat.name, error: err.message })
      }
    } else {
      categoryIdByKey.set(cat.key, `dry-run:${cat.key}`)
    }
  }

  for (const product of demo.products) {
    const categoryId = categoryIdByKey.get(product.categoryKey)
    if (!categoryId) {
      productCounters.fail += 1
      planned.push({
        type: 'product',
        action: 'fail',
        sku: product.sku,
        error: 'missing category id',
      })
      continue
    }

    const currency = demo._meta?.currency || 'VND'
    const desired = {
      sku: product.sku,
      name: product.name,
      description: product.description ?? null,
      unitPrice: product.unitPrice,
      currency,
      categoryId,
    }

    const existing = productBySku.get(product.sku.toLowerCase())
    if (existing) {
      // Dry-run placeholders mean the target grocery category is not created yet —
      // treat as update so apply will reattach after category create.
      const categoryIsPlaceholder = String(categoryId).startsWith('dry-run:')
      const unchanged =
        !categoryIsPlaceholder && productPayloadEqual(existing, desired, categoryId)

      if (unchanged) {
        productCounters.skip += 1
        planned.push({
          type: 'product',
          action: 'skip',
          sku: product.sku,
          inStock: existing.inStock,
        })
        continue
      }

      productCounters.update += 1
      planned.push({
        type: 'product',
        action: DRY_RUN ? 'would-update' : 'update',
        sku: product.sku,
        id: existing.id,
        inStock: existing.inStock,
      })
      if (!DRY_RUN) {
        try {
          const saved = await api(`/admin/products/${existing.id}`, {
            method: 'PUT',
            token,
            body: {
              sku: desired.sku,
              name: desired.name,
              description: desired.description,
              unitPrice: desired.unitPrice,
              currency: desired.currency,
              categoryId: desired.categoryId,
            },
          })
          productBySku.set(saved.sku.toLowerCase(), saved)
        } catch (err) {
          productCounters.update -= 1
          productCounters.fail += 1
          planned.push({ type: 'product', action: 'fail', sku: product.sku, error: err.message })
        }
      }
      continue
    }

    productCounters.create += 1
    planned.push({
      type: 'product',
      action: DRY_RUN ? 'would-create' : 'create',
      sku: product.sku,
      inStock: null,
      note: 'Admin create does not initialize inventory; expect inStock=null until stock seed',
    })
    if (!DRY_RUN) {
      try {
        const saved = await api('/admin/products', {
          method: 'POST',
          token,
          body: {
            sku: desired.sku,
            name: desired.name,
            description: desired.description,
            unitPrice: desired.unitPrice,
            currency: desired.currency,
            categoryId: desired.categoryId,
          },
        })
        productBySku.set(saved.sku.toLowerCase(), saved)
        planned.push({
          type: 'product',
          action: 'created-response',
          sku: saved.sku,
          inStock: saved.inStock,
        })
      } catch (err) {
        productCounters.create -= 1
        productCounters.fail += 1
        planned.push({ type: 'product', action: 'fail', sku: product.sku, error: err.message })
      }
    }
  }

  console.log('--- Category plan ---')
  console.log(summarizeCounters(categoryCounters))
  console.log('--- Product plan ---')
  console.log(summarizeCounters(productCounters))

  const sample = planned.filter((p) => p.action !== 'skip').slice(0, 15)
  if (sample.length) {
    console.log('--- Sample non-skip actions ---')
    for (const row of sample) {
      console.log(
        `  ${row.type} ${row.action} ${row.name || row.sku}${row.inStock !== undefined ? ` inStock=${row.inStock}` : ''}${row.error ? ` (${row.error})` : ''}`,
      )
    }
  }

  console.log('--- Inventory note ---')
  console.log(
    'Admin product create/update does not accept stock. New products typically return inStock=null until a separate inventory seed exists.',
  )

  if (DRY_RUN) {
    console.log('Dry-run complete. Re-run with --apply after explicit approval to write.')
  } else {
    console.log('Apply complete.')
  }

  if (categoryCounters.fail + productCounters.fail > 0) process.exit(2)
}

main().catch((err) => {
  console.error(`FATAL: ${err.message}`)
  process.exit(1)
})
