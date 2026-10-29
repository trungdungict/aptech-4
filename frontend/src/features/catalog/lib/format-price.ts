/** Format product price using the API currency (typically VND). */
export function formatProductPrice(unitPrice: string | number, currency: string): string {
  const amount = typeof unitPrice === 'number' ? unitPrice : Number(unitPrice)
  if (!Number.isFinite(amount)) {
    return String(unitPrice)
  }

  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currency || 'VND',
      maximumFractionDigits: 0,
    }).format(amount)
  }
  catch {
    return `${amount} ${currency}`
  }
}
