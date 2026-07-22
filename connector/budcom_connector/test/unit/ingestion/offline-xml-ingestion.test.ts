import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import { OfflineXmlIngestionService } from '../../../src/ingestion/offline-xml-ingestion.service.js';

describe('OfflineXmlIngestionService', () => {
  function make() {
    return new OfflineXmlIngestionService(
      new TallyXmlResponseParser(),
      createLogger({ service: 'test', level: 'error' }),
    );
  }

  it('parses an offline XML string without any Tally access', async () => {
    const svc = make();
    await svc.start();
    const result = svc.ingestString('<ENVELOPE><BODY><ITEM>A</ITEM></BODY></ENVELOPE>', 'file.xml');
    expect(result.sourceLabel).toBe('file.xml');
    expect(result.nodeCount).toBeGreaterThan(0);
    await svc.stop();
  });

  it('rejects empty input and refuses to run when stopped', async () => {
    const svc = make();
    await svc.start();
    expect(() => svc.ingestString('   ')).toThrow();
    await svc.stop();
    expect(() => svc.ingestString('<ENVELOPE></ENVELOPE>')).toThrow();
  });

  it('exposes no transport, gateway, or connection handle', () => {
    const svc = make();
    const surface = svc as unknown as Record<string, unknown>;
    expect(surface.gateway).toBeUndefined();
    expect(surface.transport).toBeUndefined();
    expect(surface.connectionManager).toBeUndefined();
  });
});
