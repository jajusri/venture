import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import https from 'node:https';
import http from 'node:http';
import type { AddressInfo } from 'node:net';

import { afterEach, describe, expect, it } from 'vitest';

import { ApiServerStub } from '../../src/services/placeholders/api-server.stub.js';
import type { ConnectorConfig } from '../../src/config/defaults.js';
import { createTestContext, resolveApiServerDeps, startTestServices } from '../helpers/test-context.js';
import { cleanupTestSqliteStorage } from '../helpers/sqlite-test-storage.js';

const tempDirs: string[] = [];
const activeServers: ApiServerStub[] = [];

afterEach(async () => {
  for (const server of activeServers.splice(0)) {
    await server.stop();
  }
  await cleanupTestSqliteStorage();
  for (const dir of tempDirs.splice(0)) {
    try {
      fs.rmSync(dir, { recursive: true, force: true, maxRetries: 3, retryDelay: 50 });
    } catch {
      // Windows may keep file handles briefly after close.
    }
  }
});

function makeTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-https-transport-test-'));
  tempDirs.push(dir);
  return dir;
}

/**
 * Builds a real ApiServerStub bound to OS-assigned ephemeral ports (0), bypassing loadConfig's
 * port-range validation (which disallows 0) exactly like the existing
 * test/helpers/fault-injection-server.ts real-socket test pattern. secureTransportEnabled and
 * secureTransportPort are the only fields under test; everything else mirrors resolveApiServerDeps.
 */
async function buildTestApiServer(overrides: {
  secureTransportEnabled: boolean;
  transportIdentityDir?: string;
}): Promise<{ context: Awaited<ReturnType<typeof createTestContext>>; apiServer: ApiServerStub }> {
  const context = createTestContext({
    transportIdentityDir: overrides.transportIdentityDir ?? makeTempDir(),
  });
  await startTestServices(context);
  const deps = resolveApiServerDeps(context);

  const testConfig: ConnectorConfig = {
    ...context.config,
    host: '127.0.0.1',
    port: 0,
    secureTransportEnabled: overrides.secureTransportEnabled,
    secureTransportPort: 0,
  };

  const apiServer = new ApiServerStub(testConfig, deps.logger, () => deps, deps.transportIdentity);
  activeServers.push(apiServer);
  return { context, apiServer };
}

function httpGet(port: number, httpsMode: boolean, requestPath = '/health'): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const client = httpsMode ? https : http;
    const req = client.get(
      { host: '127.0.0.1', port, path: requestPath, rejectUnauthorized: false },
      (res) => {
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => resolve({ status: res.statusCode ?? 0, body }));
      },
    );
    req.on('error', reject);
  });
}

describe('secureTransportEnabled default and HTTP compatibility', () => {
  it('defaults to false', () => {
    const context = createTestContext();
    expect(context.config.secureTransportEnabled).toBe(false);
  });

  it('existing HTTP listener behavior is unchanged while secureTransportEnabled is false', async () => {
    const { apiServer } = await buildTestApiServer({ secureTransportEnabled: false });
    await apiServer.start();

    expect(apiServer.getServer()).not.toBeNull();
    expect(apiServer.getSecureServer()).toBeNull();

    const httpPort = (apiServer.getServer()?.address() as AddressInfo).port;
    const response = await httpGet(httpPort, false);
    expect(response.status).toBe(200);
  });
});

describe('HTTPS listener lifecycle', () => {
  it('does not start an HTTPS listener while secureTransportEnabled is false', async () => {
    const { apiServer } = await buildTestApiServer({ secureTransportEnabled: false });
    await apiServer.start();
    expect(apiServer.getSecureServer()).toBeNull();
  });

  it('starts an HTTPS listener when secureTransportEnabled is explicitly true', async () => {
    const { apiServer } = await buildTestApiServer({ secureTransportEnabled: true });
    await apiServer.start();

    expect(apiServer.getServer()).not.toBeNull();
    expect(apiServer.getSecureServer()).not.toBeNull();

    const httpsPort = (apiServer.getSecureServer()?.address() as AddressInfo).port;
    const response = await httpGet(httpsPort, true);
    expect(response.status).toBe(200);
  });

  it('HTTP and HTTPS listeners serve the same application routes', async () => {
    const { apiServer } = await buildTestApiServer({ secureTransportEnabled: true });
    await apiServer.start();

    const httpPort = (apiServer.getServer()?.address() as AddressInfo).port;
    const httpsPort = (apiServer.getSecureServer()?.address() as AddressInfo).port;

    const httpResponse = await httpGet(httpPort, false);
    const httpsResponse = await httpGet(httpsPort, true);

    expect(httpResponse.status).toBe(200);
    expect(httpsResponse.status).toBe(200);
    // Same handler/route contract, not two independent business-logic stacks.
    expect(JSON.parse(httpsResponse.body).connectorId).toBe(JSON.parse(httpResponse.body).connectorId);
  });

  it('shuts down both listeners cleanly', async () => {
    const { apiServer } = await buildTestApiServer({ secureTransportEnabled: true });
    await apiServer.start();
    expect(apiServer.isRunning()).toBe(true);

    await apiServer.stop();

    expect(apiServer.isRunning()).toBe(false);
    expect(apiServer.getServer()).toBeNull();
    expect(apiServer.getSecureServer()).toBeNull();
  });

  it('startup fails clearly when HTTPS is requested but transport identity material is corrupt', async () => {
    const dir = makeTempDir();
    // Seed a corrupt key before the service ever generates a fresh identity.
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'transport-key.pem'), 'not a real key', 'utf8');
    fs.writeFileSync(path.join(dir, 'transport-cert.pem'), 'not a real cert', 'utf8');

    const { apiServer } = await buildTestApiServer({ secureTransportEnabled: true, transportIdentityDir: dir });

    await expect(apiServer.start()).rejects.toBeTruthy();
    expect(apiServer.getServer()).toBeNull();
  });
});
