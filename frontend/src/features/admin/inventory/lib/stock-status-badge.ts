import type { InventoryStockBucket } from '../types'

/** `admin` i18n key for the localized stock badge label (bucket enum itself is never translated). */
export function stockBucketLabelKey(
  bucket: InventoryStockBucket,
): 'inventory.stock.inStock' | 'inventory.stock.low' | 'inventory.stock.out' {
  switch (bucket) {
    case 'IN_STOCK':
      return 'inventory.stock.inStock'
    case 'LOW':
      return 'inventory.stock.low'
    case 'OUT':
      return 'inventory.stock.out'
  }
}

export function stockBucketBadgeVariant(
  bucket: InventoryStockBucket,
): 'success' | 'warning' | 'destructive' {
  switch (bucket) {
    case 'IN_STOCK':
      return 'success'
    case 'LOW':
      return 'warning'
    case 'OUT':
      return 'destructive'
  }
}
