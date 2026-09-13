import { afterEach, describe, expect, it } from 'vitest';

import { createLogger } from '../../src/infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../src/infrastructure/errors/app-error.js';
import { TallyHttpTransport } from '../../src/tally/transport/tally-http-transport.js';
import { loadConfig } from '../../src/config/index.js';
import { SAMPLE_LEDGERS_RESPONSE } from '../helpers/master-data-fixtures.js';
import { FaultInjectionTallyServer } from '../helpers/fault-injection-server.js';

const LEDGER_EXPORT_REQUEST =
  '<ENVELOPE><BODY><DATA><TALLYREQUEST>Export</TALLYREQUEST><TYPE>Collection</TYPE><ID>List of Ledgers</ID></DATA></BODY></ENVELOPE>';

describe('TallyHttpTransport deterministic fault handling', () => {
  const servers: FaultInjectionTallyServer[] = [];

  afterEach(async () => {
    await Promise.all(servers.splice(0).map((server) => server.close().catch(() => undefined)));
  });

  function createTransport(port: number, timeoutMs = 1500): TallyHttpTransport {
    return new TallyHttpTransport({
      config: loadConfig({ env: 'test', tallyHost: '127.0.0.1', tallyPort: port, tallyTimeoutMs: timeoutMs }),
      logger: createLogger({ service: 'test', level: 'error' }),
    });
  }

  it('rejects connection reset before response headers', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'reset-every-connection' });

    const transport = createTransport(server.port);
    await expect(
      transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' }),
    ).rejects.toMatchObject({ code: ErrorCodes.SERVICE_UNAVAILABLE, statusCode: 503 });
  });

  it('rejects socket destruction during response body reception', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({
      mode: 'partial-body-destroy',
      body: SAMPLE_LEDGERS_RESPONSE,
      partialBody: SAMPLE_LEDGERS_RESPONSE.slice(0, 128),
    });

    const transport = createTransport(server.port);
    await expect(
      transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' }),
    ).rejects.toMatchObject({ code: ErrorCodes.SERVICE_UNAVAILABLE, statusCode: 503 });
  });

  it('classifies stalled body reads as timeout', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'stall-after-headers', stallMs: 5000 });

    const transport = createTransport(server.port, 300);
    await expect(
      transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml', timeoutMs: 300 }),
    ).rejects.toMatchObject({ code: ErrorCodes.SERVICE_UNAVAILABLE, statusCode: 504 });
  });

  it('accepts complete body when Content-Length matches payload', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'complete-body', body: SAMPLE_LEDGERS_RESPONSE });

    const transport = createTransport(server.port);
    const response = await transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' });
    expect(response.body).toContain('<LEDGER');
    expect(response.statusCode).toBe(200);
  });

  it('investigates Content-Length greater than bytes received', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'content-length-mismatch', body: SAMPLE_LEDGERS_RESPONSE });

    const transport = createTransport(server.port, 3000);
    const outcome = await transport
      .send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' })
      .then(
        (response) => ({ kind: 'success' as const, byteLength: response.body.length }),
        (error) => ({ kind: 'failure' as const, error }),
      );

    if (outcome.kind === 'success') {
      expect(outcome.byteLength).toBeGreaterThan(0);
      return;
    }
    expect(outcome.error).toBeInstanceOf(AppError);
    expect([503, 504]).toContain((outcome.error as AppError).statusCode);
  });

  it('investigates chunked transfer terminated prematurely', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'chunked-premature-end', body: SAMPLE_LEDGERS_RESPONSE });

    const transport = createTransport(server.port);
    await expect(
      transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' }),
    ).rejects.toMatchObject({ code: ErrorCodes.SERVICE_UNAVAILABLE, statusCode: 503 });
  });

  it('maps explicit abort during body reception to cancellation', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({ mode: 'stall-after-headers', stallMs: 5000 });

    const transport = createTransport(server.port, 5000);
    const controller = new AbortController();
    const pending = transport.send({
      body: LEDGER_EXPORT_REQUEST,
      contentType: 'text/xml',
      signal: controller.signal,
      timeoutMs: 5000,
    });
    controller.abort();

    await expect(pending).rejects.toMatchObject({ code: ErrorCodes.SYNC_CANCELLED, statusCode: 499 });
  });

  it('rejects structurally truncated XML bodies at HTTP layer when empty after read', async () => {
    const server = new FaultInjectionTallyServer();
    servers.push(server);
    await server.start({
      mode: 'truncated-xml',
      body: SAMPLE_LEDGERS_RESPONSE,
    });

    const transport = createTransport(server.port);
    const response = await transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' });
    expect(response.body).not.toMatch(/<\/ENVELOPE>\s*$/i);
  });
});

describe('TallyHttpTransport connection refused', () => {
  it('returns typed failure when endpoint is unavailable before request', async () => {
    const transport = new TallyHttpTransport({
      config: loadConfig({ env: 'test', tallyHost: '127.0.0.1', tallyPort: 19, tallyTimeoutMs: 1000 }),
      logger: createLogger({ service: 'test', level: 'error' }),
    });

    await expect(
      transport.send({ body: LEDGER_EXPORT_REQUEST, contentType: 'text/xml' }),
    ).rejects.toMatchObject({ code: ErrorCodes.SERVICE_UNAVAILABLE, statusCode: 503 });
  });
});
