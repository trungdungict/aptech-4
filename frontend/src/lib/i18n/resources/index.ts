import { enAdmin } from './en/admin'
import { enAuth } from './en/auth'
import { enCart } from './en/cart'
import { enCatalog } from './en/catalog'
import { enCheckout } from './en/checkout'
import { enCommon } from './en/common'
import { enHome } from './en/home'
import { enNotifications } from './en/notifications'
import { enOrders } from './en/orders'
import { enPromotions } from './en/promotions'
import { enValidation } from './en/validation'
import { viAdmin } from './vi/admin'
import { viAuth } from './vi/auth'
import { viCart } from './vi/cart'
import { viCatalog } from './vi/catalog'
import { viCheckout } from './vi/checkout'
import { viCommon } from './vi/common'
import { viHome } from './vi/home'
import { viNotifications } from './vi/notifications'
import { viOrders } from './vi/orders'
import { viPromotions } from './vi/promotions'
import { viValidation } from './vi/validation'

/** Customer namespaces (T07) + `admin` (T08). */
export const resources = {
  vi: {
    common: viCommon,
    auth: viAuth,
    validation: viValidation,
    catalog: viCatalog,
    cart: viCart,
    checkout: viCheckout,
    orders: viOrders,
    home: viHome,
    promotions: viPromotions,
    notifications: viNotifications,
    admin: viAdmin,
  },
  en: {
    common: enCommon,
    auth: enAuth,
    validation: enValidation,
    catalog: enCatalog,
    cart: enCart,
    checkout: enCheckout,
    orders: enOrders,
    home: enHome,
    promotions: enPromotions,
    notifications: enNotifications,
    admin: enAdmin,
  },
} as const

export const CUSTOMER_NAMESPACES = [
  'common',
  'auth',
  'validation',
  'catalog',
  'cart',
  'checkout',
  'orders',
  'home',
  'promotions',
  'notifications',
] as const

/** All i18next namespaces registered at init (customer + admin). */
export const APP_NAMESPACES = [...CUSTOMER_NAMESPACES, 'admin'] as const

export type AppResources = (typeof resources)['vi']
