import { describe, expect, it } from 'vitest';

import {
  registerServices,
  startApplication,
  stopApplication,
} from '../../src/bootstrap/register-services.js';
import { ServiceTokens } from '../../src/core/tokens.js';
import type { ServiceLifecycle } from '../../src/core/types.js';
import { VoucherExtractionService } from '../../src/services/voucher/voucher-extraction.service.js';

describe('application lifecycle', () => {
  it('starts and stops all registered services in order', async () => {
    const context = registerServices({ env: 'test', logLevel: 'error', port: 9876 });

    await startApplication(context);

    expect(
      context.container.resolve<VoucherExtractionService>(ServiceTokens.VoucherExtraction),
    ).toBeInstanceOf(VoucherExtractionService);

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

describe('trusted-device reconnect independence from pairing admission', () => {
  // TD-017 physical retest found pairing-admission OFF ("Secure Mobile Pairing is off")
  // structurally gates only NEW pairing-session creation (requireSecurePairingEnabled in
  // api/routes/pairing.ts, MobilePairingService.getSecurePairingCapability() on Desktop) — never
  // MdnsAdvertiser, which register-services.ts's STARTUP_ORDER/SHUTDOWN_ORDER include
  // unconditionally, and whose own MdnsAdvertiserDeps has no securePairingEnabled field to gate
  // on in the first place. This is the explicit regression test that invariant never regresses:
  // an already-trusted device's ability to discover/reconnect to its Connector must survive
  // securePairingEnabled being false, since new-pairing admission and existing-trust
  // discoverability are deliberately independent concepts.
  it('MdnsAdvertiser keeps advertising when securePairingEnabled is false — admission gates only NEW pairing, never existing-trust discovery', async () => {
    const context = registerServices({
      env: 'test',
      logLevel: 'error',
      port: 9878,
      securePairingEnabled: false,
    });

    await startApplication(context);
    try {
      const advertiser = context.container.resolve<ServiceLifecycle>(ServiceTokens.MdnsAdvertiser);
      expect(advertiser.isRunning(), 'MdnsAdvertiser should be running with securePairingEnabled: false').toBe(true);
    } finally {
      await stopApplication(context);
    }
  });

  it('MdnsAdvertiser also advertises when securePairingEnabled is true — proving the flag genuinely has no effect either way', async () => {
    const context = registerServices({
      env: 'test',
      logLevel: 'error',
      port: 9879,
      securePairingEnabled: true,
    });

    await startApplication(context);
    try {
      const advertiser = context.container.resolve<ServiceLifecycle>(ServiceTokens.MdnsAdvertiser);
      expect(advertiser.isRunning(), 'MdnsAdvertiser should be running with securePairingEnabled: true').toBe(true);
    } finally {
      await stopApplication(context);
    }
  });
});
