/** Matches backend AdminInventoryListItemResponse. */
export type AdminInventoryListItem = {
  productId: string
  sku: string
  name: string
  active: boolean
  categoryId: string
  categoryName: string
  hasInventoryRow: boolean
  quantityOnHand: number
  quantityReserved: number
  available: number
  updatedAt: string | null
}

/** Matches backend AdminInventorySummaryResponse. */
export type AdminInventorySummary = {
  totalProducts: number
  inStockCount: number
  lowStockCount: number
  outOfStockCount: number
  lowStockThreshold: number
  availableDefinition: string
}

/** Matches backend AdminInventoryStockStatus. */
export type AdminInventoryStockStatusFilter = 'ALL' | 'IN_STOCK' | 'LOW' | 'OUT'

export type AdminInventoryListParams = {
  q?: string
  active?: boolean
  stockStatus?: AdminInventoryStockStatusFilter
  page?: number
  size?: number
  sort?: string
}

export const ADMIN_INVENTORY_DEFAULT_SORT = 'name,asc'

/** `value` is the API sort token (never translated); `labelKey` is an `admin` i18n key. */
export const ADMIN_INVENTORY_SORT_OPTIONS = [
  { value: 'name,asc', labelKey: 'inventory.sort.nameAsc' },
  { value: 'name,desc', labelKey: 'inventory.sort.nameDesc' },
  { value: 'sku,asc', labelKey: 'inventory.sort.skuAsc' },
  { value: 'sku,desc', labelKey: 'inventory.sort.skuDesc' },
  { value: 'quantityOnHand,asc', labelKey: 'inventory.sort.onHandAsc' },
  { value: 'quantityOnHand,desc', labelKey: 'inventory.sort.onHandDesc' },
  { value: 'quantityReserved,desc', labelKey: 'inventory.sort.reservedDesc' },
  { value: 'updatedAt,desc', labelKey: 'inventory.sort.updatedDesc' },
  { value: 'active,desc', labelKey: 'inventory.sort.activeFirst' },
] as const

export function isAllowedAdminInventorySort(sort: string): boolean {
  return ADMIN_INVENTORY_SORT_OPTIONS.some((option) => option.value === sort)
}

/** Display bucket aligned with backend list/summary definitions. */
export type InventoryStockBucket = 'IN_STOCK' | 'LOW' | 'OUT'

export function resolveStockBucket(
  available: number,
  lowStockThreshold: number,
): InventoryStockBucket {
  if (available <= 0) return 'OUT'
  if (available <= lowStockThreshold) return 'LOW'
  return 'IN_STOCK'
}
