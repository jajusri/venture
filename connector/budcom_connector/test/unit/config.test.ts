import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../src/config/index.js';

describe('loadConfig', () => {
  it('loads defaults', () => {
    const config = loadConfig({ env: 'test' });
    expect(config.port).toBe(8080);
    expect(config.schemaVersion).toBe('1.0.0');
    expect(config.connectorVersion).toBe('0.2.0');
  });

  it('applies overrides', () => {
    const config = loadConfig({ port: 9090, env: 'test' });
    expect(config.port).toBe(9090);
  });

  it('rejects invalid port', () => {
    expect(() => loadConfig({ port: -1 })).toThrow('Invalid port value');
  });
});
