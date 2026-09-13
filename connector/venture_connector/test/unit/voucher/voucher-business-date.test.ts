import { describe, expect, it } from 'vitest';

import {
  businessDateIsoDaysBefore,
  businessTodayIso,
} from '../../../src/services/voucher/voucher-business-date.js';

describe('businessTodayIso', () => {
  it('includes the local (IST) current day while UTC is still on the previous day', () => {
    // 2026-08-11T04:09 IST == 2026-08-10T22:39 UTC — the exact gap that dropped
    // the acceptance-test voucher: UTC's calendar day lags IST by up to 5h30m
    // every night.
    const duringUtcLag = new Date('2026-08-10T22:39:18.671Z');
    expect(businessTodayIso(duringUtcLag)).toBe('2026-08-11');
  });

  it('agrees with UTC once both are on the same calendar day', () => {
    const midday = new Date('2026-08-11T12:00:00.000Z');
    expect(businessTodayIso(midday)).toBe('2026-08-11');
  });

  it('midnight boundary: the instant IST rolls over to a new day, UTC has not yet', () => {
    // 2026-08-11T00:00:00 IST == 2026-08-10T18:30:00 UTC
    const istMidnight = new Date('2026-08-10T18:30:00.000Z');
    expect(businessTodayIso(istMidnight)).toBe('2026-08-11');
    const oneMinuteBefore = new Date('2026-08-10T18:29:00.000Z');
    expect(businessTodayIso(oneMinuteBefore)).toBe('2026-08-10');
  });

  it('never returns a day later than the actual IST calendar day (no future-day inclusion)', () => {
    // Deep in IST daytime, nowhere near a boundary — UTC is behind, never ahead.
    const istAfternoon = new Date('2026-08-11T10:00:00.000Z'); // 15:30 IST
    expect(businessTodayIso(istAfternoon)).toBe('2026-08-11');
  });
});

describe('businessDateIsoDaysBefore', () => {
  it('subtracts a plain calendar-day count from a date string', () => {
    expect(businessDateIsoDaysBefore('2026-08-11', 29)).toBe('2026-07-13');
  });

  it('crosses a month boundary', () => {
    expect(businessDateIsoDaysBefore('2026-08-01', 1)).toBe('2026-07-31');
  });

  it('crosses a financial-year boundary (India: April 1)', () => {
    expect(businessDateIsoDaysBefore('2026-04-01', 1)).toBe('2026-03-31');
  });

  it('crosses a year boundary', () => {
    expect(businessDateIsoDaysBefore('2027-01-01', 1)).toBe('2026-12-31');
  });

  it('handles a leap day correctly', () => {
    expect(businessDateIsoDaysBefore('2028-03-01', 1)).toBe('2028-02-29');
    expect(businessDateIsoDaysBefore('2028-02-29', 1)).toBe('2028-02-28');
  });

  it('never produces a result later than the input date', () => {
    const result = businessDateIsoDaysBefore('2026-08-11', 29);
    expect(result <= '2026-08-11').toBe(true);
  });
});
