import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'

type ProductStockBadgeProps = {
  inStock: boolean | null
}

export function ProductStockBadge({ inStock }: ProductStockBadgeProps) {
  const { t } = useTranslation('catalog')
  if (inStock === true) {
    return <Badge variant="success">{t('stock.inStock')}</Badge>
  }
  if (inStock === false) {
    return <Badge variant="destructive">{t('stock.outOfStock')}</Badge>
  }
  return <Badge variant="secondary">{t('stock.unknown')}</Badge>
}
