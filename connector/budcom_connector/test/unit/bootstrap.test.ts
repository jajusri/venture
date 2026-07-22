import { describe, expect, it } from 'vitest';

import {
  registerServices,
  startApplication,
  stopApplication,
} from '../../src/bootstrap/register-services.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { ServiceLifecycle } from '../../src/core/types.js';

describe('application lifecycle', () => {
  it('starts and stops all registered services in order', async () => {
    const context = registerServices({ env: 'test', logLevel: 'error', port: 9876 });

    await startApplication(context);

    const lifecycleTokens = [
      ServiceTokens.LocalDatabase,
      ServiceTokens.TallyConnection,
      ServiceTokens.XmlImport,
      ServiceTokens.CompanyDiscovery,
      ServiceTokens.MasterData,
      ServiceTokens.SyncEngine,
      ServiceTokens.Licensing,
      ServiceTokens.Scheduler,
      ServiceTokens.ApiServer,
    ] as const;

    for (const token of lifecycleTokens) {
      const service = context.container.resolve<ServiceLifecycle>(token);
      expect(service.isRunning(), `${token} should be running`).toBe(true);
    }

    await stopApplication(context);

    for (const token of lifecycleTokens) {
      const service = context.container.resolve<ServiceLifecycle>(token);
      expect(service.isRunning(), `${token} should be stopped`).toBe(false);
    }
  });
});

describe('createShutdownHandler', () => {
  it('is exported from bootstrap app module', async () => {
    const { createShutdownHandler } = await import('../../src/bootstrap/app.js');
    expect(createShutdownHandler).toBeTypeOf('function');
  });
});
