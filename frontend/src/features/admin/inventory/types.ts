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

export const ADMIN_INVENTORY_SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'name,asc', label: 'Name (A–Z)' },
  { value: 'name,desc', label: 'Name (Z–A)' },
  { value: 'sku,asc', label: 'SKU (A–Z)' },
  { value: 'sku,desc', label: 'SKU (Z–A)' },
  { value: 'quantityOnHand,asc', label: 'On hand (low → high)' },
  { value: 'quantityOnHand,desc', label: 'On hand (high → low)' },
  { value: 'quantityReserved,desc', label: 'Reserved (high → low)' },
  { value: 'updatedAt,desc', label: 'Updated (newest)' },
  { value: 'active,desc', label: 'Active first' },
]

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
