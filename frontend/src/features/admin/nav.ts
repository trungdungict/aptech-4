export type AdminNavId =
  | 'overview'
  | 'products'
  | 'inventory'
  | 'analytics'
  | 'categories'
  | 'orders'
  | 'promotions'

/**
 * Admin nav entry. Display copy lives in the `admin` i18n namespace under
 * `nav.<id>.label` / `nav.<id>.description` (translated at render time).
 */
export type AdminNavItem = {
  id: AdminNavId
  /** Implemented path, or null when the module is not yet available. */
  to: string | null
}

/** Navigation for the admin shell. */
export const ADMIN_NAV_ITEMS: AdminNavItem[] = [
  { id: 'overview', to: '/admin' },
  { id: 'products', to: '/admin/products' },
  { id: 'inventory', to: '/admin/inventory' },
  { id: 'analytics', to: '/admin/analytics' },
  { id: 'categories', to: '/admin/categories' },
  { id: 'orders', to: '/admin/orders' },
  { id: 'promotions', to: '/admin/promotions' },
]
