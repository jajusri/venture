import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import express from 'express';
import request from 'supertest';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { createRequestLoggingMiddleware } from '../../../src/api/middleware/request-logging.js';
import {
  StartupValidationError,
  validateStartupConfiguration,
} from '../../../src/bootstrap/startup-validation.js';
import { defaultConfig } from '../../../src/config/defaults.js';
import { createLogger, type StructuredLogEntry } from '../../../src/infrastructure/logging/logger.js';
import { ServiceTokens } from '../../../src/core/tokens.js';
import type { ServiceLifecycle } from '../../../src/core/types.js';
import { stopApplication } from '../../../src/bootstrap/register-services.js';
import { startApplication } from '../../../src/bootstrap/register-services.js';
import { createTestApp, createTestContext, startTestServices } from '../../helpers/test-context.js';

const temporaryDirectories: string[] = [];

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

function temporaryDirectory(): string {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-voucher-hardening-'));
  temporaryDirectories.push(directory);
  return directory;
}

describe('Voucher production hardening', () => {
  it('validates and creates required writable startup directories', () => {
    const root = temporaryDirectory();
    const databasePath = path.join(root, 'data');
    const auditPath = path.join(root, 'diagnostics', 'audit.jsonl');

    expect(() =>
      validateStartupConfiguration({ ...defaultConfig, databasePath, tallyRequestAuditPath: auditPath })
    ).not.toThrow();
    expect(fs.statSync(databasePath).isDirectory()).toBe(true);
    expect(fs.statSync(path.dirname(auditPath)).isDirectory()).toBe(true);
  });

  it('fails fast with actionable configuration diagnostics', () => {
    expect(() =>
      validateStartupConfiguration({
        ...defaultConfig,
        databasePath: '',
        tallyHost: ' ',
        tallyTimeoutMs: 0,
      })
    ).toThrow(StartupValidationError);
  });

  it('reports local health and readiness without contacting Tally', async () => {
    const tallyFetch = vi.fn(() => {
      throw new Error('Health must not contact Tally');
    });
    const context = createTestContext({
      databasePath: temporaryDirectory(),
      fetchImpl: tallyFetch as typeof fetch,
    });
    await startTestServices(context);

    const health = await request(createTestApp(context)).get('/health');
    const ready = await request(createTestApp(context)).get('/ready');

    expect(health.status).toBe(200);
    expect(health.body).toMatchObject({
      repositoryAvailable: true,
      databaseAccessible: true,
      readOnly: true,
    });
    expect(ready.status).toBe(200);
    expect(ready.body).toMatchObject({
      status: 'ready',
      voucherSynchronizationComposed: true,
      voucherApplicationComposed: true,
    });
    expect(tallyFetch).not.toHaveBeenCalled();

    await stopApplication(context);
  });

  it('returns not-ready when storage has not started', async () => {
    const response = await request(createTestApp(createTestContext())).get('/ready');
    expect(response.status).toBe(503);
    expect(response.body).toMatchObject({
      status: 'not_ready',
      repositoryAvailable: false,
      databaseAccessible: false,
    });
  });

  it('logs completed API requests with a bounded correlation identifier', async () => {
    const entries: StructuredLogEntry[] = [];
    const logger = createLogger({
      service: 'hardening-test',
      level: 'info',
      sink: (entry) => entries.push(entry),
    });
    const app = express();
    app.use(createRequestLoggingMiddleware(logger));
    app.get('/probe', (_req, res) => res.status(204).end());

    const response = await request(app)
      .get('/probe')
      .set('x-correlation-id', 'voucher-hardening-test');

    expect(response.headers['x-correlation-id']).toBe('voucher-hardening-test');
    expect(entries).toContainEqual(expect.objectContaining({
      message: 'api.request.completed',
      context: expect.objectContaining({
        correlationId: 'voucher-hardening-test',
        httpMethod: 'GET',
        httpRoute: '/probe',
        statusCode: 204,
      }),
    }));
  });

  it('releases SQLite resources on shutdown', async () => {
    const context = createTestContext({ databasePath: temporaryDirectory() });
    const storage = context.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase);
    await storage.start();
    expect(storage.isRunning()).toBe(true);
    await storage.stop();
    expect(storage.isRunning()).toBe(false);
  });

  it('unwinds already-started resources after a startup failure', async () => {
    const context = createTestContext({
      databasePath: temporaryDirectory(),
      port: 65_535,
    });
    const scheduler = context.container.resolve<ServiceLifecycle>(ServiceTokens.Scheduler);
    scheduler.start = async () => {
      throw new Error('injected startup failure');
    };

    await expect(startApplication(context)).rejects.toThrow('injected startup failure');
    expect(
      context.container.resolve<ServiceLifecycle>(ServiceTokens.LocalDatabase).isRunning(),
    ).toBe(false);
    expect(
      context.container.resolve<ServiceLifecycle>(ServiceTokens.TallyConnection).isRunning(),
    ).toBe(false);
  });
});
