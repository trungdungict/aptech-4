import type { InventoryStockBucket } from '../types'

export function stockBucketLabel(bucket: InventoryStockBucket): string {
  switch (bucket) {
    case 'IN_STOCK':
      return 'In stock'
    case 'LOW':
      return 'Low stock'
    case 'OUT':
      return 'Out of stock'
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
