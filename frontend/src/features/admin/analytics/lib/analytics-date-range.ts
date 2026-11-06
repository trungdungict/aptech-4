import { i18n } from '@/lib/i18n'

/**
 * URL ↔ Instant helpers for analytics date controls.
 * Does not reimplement backend AnalyticsDateRange.normalize formulas.
 */

const MS_PER_DAY = 86_400_000
const MAX_RANGE_DAYS = 366

/** UTC calendar day `yyyy-MM-dd` → Instant at 00:00:00.000Z */
export function utcDayStartIso(yyyyMmDd: string): string {
  return `${yyyyMmDd}T00:00:00.000Z`
}

/** Inclusive end calendar day → exclusive `to` Instant (next UTC midnight). */
export function exclusiveToFromInclusiveEndDate(yyyyMmDd: string): string {
  const [y, m, d] = yyyyMmDd.split('-').map(Number)
  const next = new Date(Date.UTC(y, m - 1, d + 1))
  return next.toISOString().replace(/\.\d{3}Z$/, '.000Z')
}

/** Instant → UTC `yyyy-MM-dd` for `<input type="date">`. */
export function instantToUtcDateInput(instant: string): string {
  const date = new Date(instant)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toISOString().slice(0, 10)
}

/**
 * Exclusive Instant `to` → inclusive end calendar day for the date input.
 * Midnight UTC means the last inclusive day is the previous calendar day.
 */
export function inclusiveEndFromExclusiveTo(toInstant: string): string {
  const date = new Date(toInstant)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const iso = date.toISOString()
  const isMidnight = /T00:00:00(\.0+)?Z$/.test(iso)
  if (isMidnight) {
    return new Date(date.getTime() - MS_PER_DAY).toISOString().slice(0, 10)
  }
  return iso.slice(0, 10)
}

export function validateInclusiveUtcRange(
  fromDay: string,
  toInclusiveDay: string,
): string | null {
  if (!fromDay || !toInclusiveDay) {
    return i18n.t('analytics.range.bothRequired', { ns: 'admin' })
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fromDay) || !/^\d{4}-\d{2}-\d{2}$/.test(toInclusiveDay)) {
    return i18n.t('analytics.range.format', { ns: 'admin' })
  }
  if (fromDay > toInclusiveDay) {
    return i18n.t('analytics.range.fromAfterEnd', { ns: 'admin' })
  }
  const fromMs = Date.parse(utcDayStartIso(fromDay))
  const toMs = Date.parse(exclusiveToFromInclusiveEndDate(toInclusiveDay))
  if (!Number.isFinite(fromMs) || !Number.isFinite(toMs) || !(fromMs < toMs)) {
    return i18n.t('analytics.range.fromBeforeEnd', { ns: 'admin' })
  }
  const days = (toMs - fromMs) / MS_PER_DAY
  if (days > MAX_RANGE_DAYS) {
    return i18n.t('analytics.range.maxDays', { ns: 'admin', days: MAX_RANGE_DAYS })
  }
  return null
}

/** Preset window using browser clock Instants (backend accepts as-is when both set). */
export function presetRangeInstants(
  days: number,
  now: Date = new Date(),
): { from: string; to: string } {
  const to = now.toISOString()
  const from = new Date(now.getTime() - days * MS_PER_DAY).toISOString()
  return { from, to }
}

export function buildAppliedInstantsFromDateInputs(
  fromDay: string,
  toInclusiveDay: string,
): { from: string; to: string } | { error: string } {
  const error = validateInclusiveUtcRange(fromDay, toInclusiveDay)
  if (error) {
    return { error }
  }
  return {
    from: utcDayStartIso(fromDay),
    to: exclusiveToFromInclusiveEndDate(toInclusiveDay),
  }
}
