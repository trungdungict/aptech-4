import { formatDateTime, getActiveLocale, i18n, toIntlLocale } from '@/lib/i18n'

export function formatNotificationTime(iso: string): string {
  try {
    const date = new Date(iso)
    const now = new Date()
    const sameDay =
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate()

    if (sameDay) {
      const time = new Intl.DateTimeFormat(toIntlLocale(getActiveLocale()), {
        hour: 'numeric',
        minute: '2-digit',
      }).format(date)
      return i18n.t('notifications:today', { time })
    }

    return formatDateTime(date)
  } catch {
    return iso
  }
}
