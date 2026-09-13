/**
 * Business/accounting-day calendar date, independent of the Connector host's
 * own UTC calendar day. India does not observe DST, so a fixed IANA zone is
 * sufficient here; no company/system-level timezone setting exists anywhere
 * else in the Connector to source this from (verified: no timezone field on
 * the session/company model, no timezone dependency in package.json).
 *
 * Computing "today" from `new Date().toISOString()` (raw UTC) instead of this
 * loses the current business day for roughly 5.5 hours every night: UTC is
 * still on yesterday's date from 00:00–05:29 IST.
 */
const BUSINESS_TIME_ZONE = 'Asia/Kolkata';

/** Today's date (YYYY-MM-DD) in the business timezone. */
export function businessTodayIso(now: Date = new Date()): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(now);
}

/**
 * A pure calendar-day offset from a YYYY-MM-DD date, independent of any
 * timezone/instant — operates on the date triple itself, so it cannot
 * reintroduce a UTC/local mismatch regardless of when it's called.
 */
export function businessDateIsoDaysBefore(dateIso: string, days: number): string {
  const [year, month, day] = dateIso.split('-').map(Number);
  const anchor = new Date(Date.UTC(year, month - 1, day));
  anchor.setUTCDate(anchor.getUTCDate() - days);
  return anchor.toISOString().slice(0, 10);
}
