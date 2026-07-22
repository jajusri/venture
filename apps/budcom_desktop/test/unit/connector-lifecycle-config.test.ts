import { describe, expect, it } from 'vitest';

import {
  resolveConnectorLifecycleConfig,
  validateConnectorExecutable,
} from '../../src/application/connector-lifecycle-config.js';

describe('connector-lifecycle-config', () => {
  it('resolves connector URL and port from environment', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorBaseUrl: 'http://localhost:9090',
      autoStart: true,
    });

    expect(config.connectorPort).toBe(9090);
    expect(config.autoStart).toBe(true);
  });

  it('reports missing executable script', () => {
    const config = resolveConnectorLifecycleConfig({
      connectorExecutable: process.execPath,
      connectorArgs: ['C:/missing/connector/main.js'],
      connectorCwd: process.cwd(),
    });

    expect(validateConnectorExecutable(config)).toContain('not found');
  });
});
