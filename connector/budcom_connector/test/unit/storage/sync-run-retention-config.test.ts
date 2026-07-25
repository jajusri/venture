import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../../src/config/index.js';
import {
  SYNC_RUN_HISTORY_MAX_AGE_DAYS_DEFAULT,
  SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT,
  SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN,
  SYNC_RUN_HISTORY_MAX_COUNT_DEFAULT,
  SYNC_RUN_HISTORY_MAX_COUNT_LIMIT,
  SYNC_RUN_HISTORY_MAX_COUNT_MIN,
} from '../../../src/config/defaults.js';

describe('sync run history retention configuration (B2c)', () => {
  it('defaults to 100 rows and 90 days per company and resource kind', () => {
    const config = loadConfig({ env: 'test' });
    expect(config.syncRunHistoryMaxCount).toBe(SYNC_RUN_HISTORY_MAX_COUNT_DEFAULT);
    expect(config.syncRunHistoryMaxAgeDays).toBe(SYNC_RUN_HISTORY_MAX_AGE_DAYS_DEFAULT);
    expect(config.syncRunHistoryMaxCount).toBe(100);
    expect(config.syncRunHistoryMaxAgeDays).toBe(90);
  });

  it('accepts valid environment overrides', () => {
    const previousCount = process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_COUNT;
    const previousAge = process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_AGE_DAYS;
    process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_COUNT = '50';
    process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_AGE_DAYS = '30';
    try {
      const config = loadConfig({ env: 'test' });
      expect(config.syncRunHistoryMaxCount).toBe(50);
      expect(config.syncRunHistoryMaxAgeDays).toBe(30);
    } finally {
      if (previousCount === undefined) {
        delete process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_COUNT;
      } else {
        process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_COUNT = previousCount;
      }
      if (previousAge === undefined) {
        delete process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_AGE_DAYS;
      } else {
        process.env.BUDCOM_SYNC_RUN_HISTORY_MAX_AGE_DAYS = previousAge;
      }
    }
  });

  it.each([
    ['syncRunHistoryMaxCount', 0],
    ['syncRunHistoryMaxCount', -1],
    ['syncRunHistoryMaxAgeDays', 0],
    ['syncRunHistoryMaxAgeDays', -1],
  ])('rejects zero and negative %s override', (field, value) => {
    expect(() => loadConfig({ env: 'test', [field]: value })).toThrow(/Invalid/);
  });

  it('rejects sync run history max count below minimum', () => {
    expect(() =>
      loadConfig({ env: 'test', syncRunHistoryMaxCount: SYNC_RUN_HISTORY_MAX_COUNT_MIN - 1 }),
    ).toThrow(/Invalid sync run history max count/);
  });

  it('rejects sync run history max count above limit', () => {
    expect(() =>
      loadConfig({ env: 'test', syncRunHistoryMaxCount: SYNC_RUN_HISTORY_MAX_COUNT_LIMIT + 1 }),
    ).toThrow(/Invalid sync run history max count/);
  });

  it('rejects sync run history max age days below minimum', () => {
    expect(() =>
      loadConfig({ env: 'test', syncRunHistoryMaxAgeDays: SYNC_RUN_HISTORY_MAX_AGE_DAYS_MIN - 1 }),
    ).toThrow(/Invalid sync run history max age days/);
  });

  it('rejects sync run history max age days above limit', () => {
    expect(() =>
      loadConfig({ env: 'test', syncRunHistoryMaxAgeDays: SYNC_RUN_HISTORY_MAX_AGE_DAYS_LIMIT + 1 }),
    ).toThrow(/Invalid sync run history max age days/);
  });
});
