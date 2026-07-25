import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { OfflineXmlIngestionService } from '../../../src/ingestion/offline-xml-ingestion.service.js';
import { SAMPLE_LEDGERS_RESPONSE } from '../../helpers/master-data-fixtures.js';

describe('OfflineXmlIngestionService', () => {
  function make() {
    return new OfflineXmlIngestionService(createLogger({ service: 'test', level: 'error' }));
  }

  it('parses an offline XML string through the unified envelope boundary', async () => {
    const svc = make();
    await svc.start();
    const result = svc.ingestString(SAMPLE_LEDGERS_RESPONSE, 'file.xml', {
      targetCompanyName: 'Pilot Co',
    });
    expect(result.sourceLabel).toBe('file.xml');
    expect(result.nodeCount).toBeGreaterThan(0);
    expect(result.resourceKind).toBe('ledgers');
    expect(result.validationStatus).toBe('validated');
    await svc.stop();
  });

  it('rejects empty input and refuses to run when stopped', async () => {
    const svc = make();
    await svc.start();
    expect(() => svc.ingestString('   ')).toThrow();
    await svc.stop();
    expect(() =>
      svc.ingestString(SAMPLE_LEDGERS_RESPONSE, 'file.xml', { targetCompanyName: 'Pilot Co' }),
    ).toThrow();
  });

  it('exposes no transport, gateway, or connection handle', () => {
    const svc = make();
    const surface = svc as unknown as Record<string, unknown>;
    expect(surface.gateway).toBeUndefined();
    expect(surface.transport).toBeUndefined();
    expect(surface.connectionManager).toBeUndefined();
  });
});
