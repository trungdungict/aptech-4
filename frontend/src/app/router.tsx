import { Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'

import {
  LazyAdminCategoryFormPage,
  LazyAdminCategoryListPage,
  LazyAdminLayout,
  LazyAdminOrderDetailPage,
  LazyAdminOrderListPage,
  LazyAdminOverviewPage,
  LazyAdminProductFormPage,
  LazyAdminProductListPage,
} from '@/features/admin/admin-lazy-routes'
import { AdminRouteFallback } from '@/features/admin/components/AdminRouteFallback'
import { RequireAdmin } from '@/features/auth/components/RequireAdmin'
import { RequireAuth } from '@/features/auth/components/RequireAuth'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { CartPage } from '@/features/cart/pages/CartPage'
import { ProductDetailPage } from '@/features/catalog/pages/ProductDetailPage'
import { ProductListPage } from '@/features/catalog/pages/ProductListPage'
import { CheckoutPage } from '@/features/checkout/pages/CheckoutPage'
import { OrderDetailPage } from '@/features/orders/pages/OrderDetailPage'
import { OrderHistoryPage } from '@/features/orders/pages/OrderHistoryPage'
import { RootLayout } from '@/components/layout/RootLayout'
import { HomePage } from '@/pages/HomePage'

export function AppRouter() {
  return (
    <Routes>
      <Route
        path="admin"
        element={
          <RequireAdmin>
            <Suspense fallback={<AdminRouteFallback />}>
              <LazyAdminLayout />
            </Suspense>
          </RequireAdmin>
        }
      >
        <Route index element={<LazyAdminOverviewPage />} />
        <Route path="products" element={<LazyAdminProductListPage />} />
        <Route path="products/new" element={<LazyAdminProductFormPage mode="create" />} />
        <Route
          path="products/:productId/edit"
          element={<LazyAdminProductFormPage mode="edit" />}
        />
        <Route path="categories" element={<LazyAdminCategoryListPage />} />
        <Route path="categories/new" element={<LazyAdminCategoryFormPage mode="create" />} />
        <Route
          path="categories/:categoryId/edit"
          element={<LazyAdminCategoryFormPage mode="edit" />}
        />
        <Route path="orders" element={<LazyAdminOrderListPage />} />
        <Route path="orders/:orderId" element={<LazyAdminOrderDetailPage />} />
      </Route>

      <Route element={<RootLayout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="products" element={<ProductListPage />} />
        <Route path="products/:productId" element={<ProductDetailPage />} />
        <Route
          path="cart"
          element={
            <RequireAuth>
              <CartPage />
            </RequireAuth>
          }
        />
        <Route
          path="checkout"
          element={
            <RequireAuth>
              <CheckoutPage />
            </RequireAuth>
          }
        />
        <Route
          path="orders"
          element={
            <RequireAuth>
              <OrderHistoryPage />
            </RequireAuth>
          }
        />
        <Route
          path="orders/:orderId"
          element={
            <RequireAuth>
              <OrderDetailPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
