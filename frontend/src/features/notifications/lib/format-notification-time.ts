export function formatNotificationTime(iso: string): string {
  try {
    const date = new Date(iso)
    const now = new Date()
    const sameDay =
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate()

    if (sameDay) {
      return `Today at ${new Intl.DateTimeFormat(undefined, {
        hour: 'numeric',
        minute: '2-digit',
      }).format(date)}`
    }

    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date)
  } catch {
    return iso
  }
}
