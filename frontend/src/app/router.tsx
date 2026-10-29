import { Navigate, Route, Routes } from 'react-router-dom'

import { AdminLayout } from '@/features/admin/components/AdminLayout'
import { AdminCategoryFormPage } from '@/features/admin/categories/pages/AdminCategoryFormPage'
import { AdminCategoryListPage } from '@/features/admin/categories/pages/AdminCategoryListPage'
import { AdminOrderDetailPage } from '@/features/admin/orders/pages/AdminOrderDetailPage'
import { AdminOrderListPage } from '@/features/admin/orders/pages/AdminOrderListPage'
import { AdminOverviewPage } from '@/features/admin/pages/AdminOverviewPage'
import { AdminProductFormPage } from '@/features/admin/products/pages/AdminProductFormPage'
import { AdminProductListPage } from '@/features/admin/products/pages/AdminProductListPage'
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
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route index element={<AdminOverviewPage />} />
        <Route path="products" element={<AdminProductListPage />} />
        <Route path="products/new" element={<AdminProductFormPage mode="create" />} />
        <Route path="products/:productId/edit" element={<AdminProductFormPage mode="edit" />} />
        <Route path="categories" element={<AdminCategoryListPage />} />
        <Route path="categories/new" element={<AdminCategoryFormPage mode="create" />} />
        <Route
          path="categories/:categoryId/edit"
          element={<AdminCategoryFormPage mode="edit" />}
        />
        <Route path="orders" element={<AdminOrderListPage />} />
        <Route path="orders/:orderId" element={<AdminOrderDetailPage />} />
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
