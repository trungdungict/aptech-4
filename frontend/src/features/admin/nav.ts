export type AdminNavItem = {
  id: string
  label: string
  /** Implemented path, or null when the module is not yet available. */
  to: string | null
  description: string
}

/** Navigation for the admin shell. */
export const ADMIN_NAV_ITEMS: AdminNavItem[] = [
  {
    id: 'overview',
    label: 'Overview',
    to: '/admin',
    description: 'Admin workspace landing',
  },
  {
    id: 'products',
    label: 'Products',
    to: '/admin/products',
    description: 'Create, edit, and soft-disable products',
  },
  {
    id: 'categories',
    label: 'Categories',
    to: '/admin/categories',
    description: 'Create, edit, and soft-disable categories',
  },
  {
    id: 'orders',
    label: 'Orders',
    to: '/admin/orders',
    description: 'List orders and update fulfillment status',
  },
]
