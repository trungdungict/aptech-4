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
    return 'Both dates are required'
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fromDay) || !/^\d{4}-\d{2}-\d{2}$/.test(toInclusiveDay)) {
    return 'Dates must use YYYY-MM-DD'
  }
  if (fromDay > toInclusiveDay) {
    return 'From date must be on or before end date'
  }
  const fromMs = Date.parse(utcDayStartIso(fromDay))
  const toMs = Date.parse(exclusiveToFromInclusiveEndDate(toInclusiveDay))
  if (!Number.isFinite(fromMs) || !Number.isFinite(toMs) || !(fromMs < toMs)) {
    return 'From date must be before end date'
  }
  const days = (toMs - fromMs) / MS_PER_DAY
  if (days > MAX_RANGE_DAYS) {
    return `Date range must be at most ${MAX_RANGE_DAYS} days`
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
