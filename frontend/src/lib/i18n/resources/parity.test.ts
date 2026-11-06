import { describe, expect, it } from 'vitest'

import { APP_NAMESPACES, resources } from './index'

function flatKeys(value: unknown, prefix = ''): string[] {
  if (value === null || typeof value !== 'object') {
    return [prefix]
  }
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    flatKeys(child, prefix ? `${prefix}.${key}` : key),
  )
}

describe('app i18n resource parity (customer + admin)', () => {
  it.each(APP_NAMESPACES)('vi and en expose the same keys for %s', (ns) => {
    expect(flatKeys(resources.en[ns]).sort()).toEqual(flatKeys(resources.vi[ns]).sort())
  })
})
