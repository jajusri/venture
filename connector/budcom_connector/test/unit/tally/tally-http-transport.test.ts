import { describe, expect, it } from 'vitest';

import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { TallyHttpTransport } from '../../../src/tally/transport/tally-http-transport.js';
import { loadConfig } from '../../../src/config/index.js';
import { createMockFetch } from '../../helpers/mock-fetch.js';

describe('TallyHttpTransport', () => {
  it('posts XML to configured Tally endpoint', async () => {
    const { fetchImpl, calls } = createMockFetch(() => ({
      body: '<ENVELOPE><BODY>ok</BODY></ENVELOPE>',
    }));
    const transport = new TallyHttpTransport({
      config: loadConfig({ env: 'test', tallyHost: '127.0.0.1', tallyPort: 9000 }),
      logger: createLogger({ service: 'test', level: 'error' }),
      fetchImpl,
    });

    const response = await transport.send({
      body: '<ENVELOPE></ENVELOPE>',
      contentType: 'text/xml',
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toContain('ok');
    expect(calls[0]?.url).toBe('http://127.0.0.1:9000');
    expect(calls[0]?.init?.method).toBe('POST');
  });

  it('throws on HTTP error responses', async () => {
    const { fetchImpl } = createMockFetch(() => ({
      status: 500,
      body: 'error',
    }));
    const transport = new TallyHttpTransport({
      config: loadConfig({ env: 'test' }),
      logger: createLogger({ service: 'test', level: 'error' }),
      fetchImpl,
    });

    await expect(
      transport.send({ body: '<ENVELOPE></ENVELOPE>', contentType: 'text/xml' }),
    ).rejects.toMatchObject({ statusCode: 503 });
  });

  it('throws on empty response body', async () => {
    const { fetchImpl } = createMockFetch(() => ({ body: '   ' }));
    const transport = new TallyHttpTransport({
      config: loadConfig({ env: 'test' }),
      logger: createLogger({ service: 'test', level: 'error' }),
      fetchImpl,
    });

    await expect(
      transport.send({ body: '<ENVELOPE></ENVELOPE>', contentType: 'text/xml' }),
    ).rejects.toMatchObject({ statusCode: 503 });
  });
});
