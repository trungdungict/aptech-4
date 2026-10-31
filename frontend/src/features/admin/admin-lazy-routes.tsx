import { lazy, type ComponentType } from 'react'

/**
 * Admin-only code-split entry points. Customer routes stay eager in the router.
 * Named exports are remapped to default for React.lazy.
 */
function lazyNamed<TModule, TProps extends object>(
  loader: () => Promise<TModule>,
  pick: (module: TModule) => ComponentType<TProps>,
) {
  return lazy(async () => {
    const module = await loader()
    return { default: pick(module) }
  })
}

export const LazyAdminLayout = lazyNamed(
  () => import('@/features/admin/components/AdminLayout'),
  (m) => m.AdminLayout,
)

export const LazyAdminOverviewPage = lazyNamed(
  () => import('@/features/admin/pages/AdminOverviewPage'),
  (m) => m.AdminOverviewPage,
)

export const LazyAdminProductListPage = lazyNamed(
  () => import('@/features/admin/products/pages/AdminProductListPage'),
  (m) => m.AdminProductListPage,
)

export const LazyAdminProductFormPage = lazyNamed(
  () => import('@/features/admin/products/pages/AdminProductFormPage'),
  (m) => m.AdminProductFormPage,
)

export const LazyAdminCategoryListPage = lazyNamed(
  () => import('@/features/admin/categories/pages/AdminCategoryListPage'),
  (m) => m.AdminCategoryListPage,
)

export const LazyAdminCategoryFormPage = lazyNamed(
  () => import('@/features/admin/categories/pages/AdminCategoryFormPage'),
  (m) => m.AdminCategoryFormPage,
)

export const LazyAdminOrderListPage = lazyNamed(
  () => import('@/features/admin/orders/pages/AdminOrderListPage'),
  (m) => m.AdminOrderListPage,
)

export const LazyAdminOrderDetailPage = lazyNamed(
  () => import('@/features/admin/orders/pages/AdminOrderDetailPage'),
  (m) => m.AdminOrderDetailPage,
)
