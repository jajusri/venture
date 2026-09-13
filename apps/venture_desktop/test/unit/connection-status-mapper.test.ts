import { describe, expect, it } from 'vitest';

import {
  getConnectionLabel,
  mapConnectionIndicator,
} from '../../src/application/connection-status-mapper.js';
import type { HealthResponse } from '../../src/application/types.js';

const baseHealth: HealthResponse = {
  status: 'ok',
  schemaVersion: '1.0.0',
  connectorVersion: '0.3.1',
  tallyReachable: true,
  readOnly: true,
  services: [
    { name: 'ApiServer', running: true, ready: true, message: 'Running' },
    { name: 'TallyConnection', running: true, ready: true, message: 'State: connected' },
  ],
};

describe('connection-status-mapper', () => {
  it('maps reachable healthy connector to connected', () => {
    expect(mapConnectionIndicator(true, baseHealth)).toBe('connected');
    expect(getConnectionLabel('connected')).toBe('Connected');
  });

  it('maps unreachable connector to disconnected', () => {
    expect(mapConnectionIndicator(false, null)).toBe('disconnected');
    expect(getConnectionLabel('disconnected')).toBe('Disconnected');
  });

  it('maps unavailable tally to disconnected', () => {
    expect(
      mapConnectionIndicator(true, { ...baseHealth, status: 'unavailable', tallyReachable: false }),
    ).toBe('disconnected');
  });

  it('maps degraded health to waiting', () => {
    expect(mapConnectionIndicator(true, { ...baseHealth, status: 'degraded' })).toBe('waiting');
    expect(getConnectionLabel('waiting')).toBe('Waiting');
  });

  it('maps connecting tally state to starting', () => {
    expect(
      mapConnectionIndicator(true, {
        ...baseHealth,
        services: [
          { name: 'ApiServer', running: true, ready: true },
          { name: 'TallyConnection', running: true, ready: false, message: 'State: connecting' },
        ],
      }),
    ).toBe('starting');
    expect(getConnectionLabel('starting')).toBe('Starting');
  });

  it('maps unavailable api server to starting or error', () => {
    expect(
      mapConnectionIndicator(true, {
        ...baseHealth,
        status: 'unavailable',
        services: [{ name: 'ApiServer', running: false, ready: false, message: 'Stopped' }],
      }),
    ).toBe('starting');
  });
});
