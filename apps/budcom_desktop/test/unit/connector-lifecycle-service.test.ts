import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

import type {
  ConnectorLifecycleConfig,
  HealthChecker,
  ManagedProcess,
  ProcessSpawner,
  SpawnSpec,
} from '../../src/application/connector-lifecycle-types.js';
import { ConnectorLifecycleService } from '../../src/application/connector-lifecycle-service.js';
import { LogService } from '../../src/application/log-service.js';

class MockManagedProcess implements ManagedProcess {
  readonly pid = 4242;
  private exitListener: ((code: number | null, signal: NodeJS.Signals | null) => void) | null = null;

  kill = vi.fn(async () => undefined);

  onExit(listener: (code: number | null, signal: NodeJS.Signals | null) => void): void {
    this.exitListener = listener;
  }

  emitExit(code: number | null = 1, signal: NodeJS.Signals | null = null): void {
    this.exitListener?.(code, signal);
  }
}

class MockProcessSpawner implements ProcessSpawner {
  spawn = vi.fn((_spec: SpawnSpec) => new MockManagedProcess());
}

class MockHealthChecker implements HealthChecker {
  checkHealth = vi.fn(async () => false);
  checkHealthDetails = vi.fn(async () => ({ ready: false, owned: false }));
}

const baseConfig: ConnectorLifecycleConfig = {
  connectorBaseUrl: 'http://localhost:8080',
  connectorHost: '127.0.0.1',
  connectorPort: 8080,
  connectorExecutable: process.execPath,
  connectorArgs: [process.execPath],
  connectorCwd: process.cwd(),
  autoStart: false,
  healthPollIntervalMs: 1_000,
  startupTimeoutMs: 2_000,
  shutdownGraceMs: 10,
  maxRestartAttempts: 3,
  reconnectBaseDelayMs: 100,
  staleHealthThresholdMs: 5_000,
};

function createService(options?: {
  healthChecker?: MockHealthChecker;
  processSpawner?: MockProcessSpawner;
  config?: Partial<ConnectorLifecycleConfig>;
  sleep?: (ms: number) => Promise<void>;
}): {
  service: ConnectorLifecycleService;
  healthChecker: MockHealthChecker;
  processSpawner: MockProcessSpawner;
  logService: LogService;
} {
  const healthChecker = options?.healthChecker ?? new MockHealthChecker();
  const processSpawner = options?.processSpawner ?? new MockProcessSpawner();
  const logService = new LogService();
  const service = new ConnectorLifecycleService({
    config: { ...baseConfig, ...options?.config },
    processSpawner,
    healthChecker,
    logService,
    sleep: options?.sleep ?? (async () => undefined),
  });
  return { service, healthChecker, processSpawner, logService };
}

describe('ConnectorLifecycleService', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('detects an already running connector and avoids duplicate launch', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails.mockResolvedValue({ ready: true, owned: true });
    const { service, processSpawner } = createService({ healthChecker });

    const status = await service.ensureConnectorRunning();

    expect(status.state).toBe('connected');
    expect(status.externalProcessDetected).toBe(true);
    expect(status.managedByDesktop).toBe(false);
    expect(processSpawner.spawn).not.toHaveBeenCalled();
  });

  it('starts managed connector when health is unavailable', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: true, owned: true });
    const { service, processSpawner } = createService({ healthChecker });

    const status = await service.ensureConnectorRunning();

    expect(processSpawner.spawn).toHaveBeenCalledTimes(1);
    expect(status.managedByDesktop).toBe(true);
    expect(status.state).toBe('connected');
  });

  it('ignores unrelated connector health on a different ownership contract', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails
      .mockResolvedValueOnce({ ready: true, owned: false, bindPort: 8080, startupCorrelationId: 'other' })
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: true, owned: true });
    const { service, processSpawner } = createService({ healthChecker });

    const status = await service.ensureConnectorRunning();

    expect(processSpawner.spawn).toHaveBeenCalledTimes(1);
    expect(status.managedByDesktop).toBe(true);
  });

  it('prevents duplicate launch while managed process exists', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: true, owned: true })
      .mockResolvedValueOnce({ ready: true, owned: true });
    const { service, processSpawner } = createService({ healthChecker });

    await service.ensureConnectorRunning();
    await service.ensureConnectorRunning();

    expect(processSpawner.spawn).toHaveBeenCalledTimes(1);
  });

  it('handles startup timeout', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails.mockResolvedValue({ ready: false, owned: false });
    const { service } = createService({
      healthChecker,
      config: { startupTimeoutMs: 500 },
      sleep: async () => undefined,
    });

    const status = await service.ensureConnectorRunning();

    expect(status.state).toBe('failed');
    expect(status.userMessage).toContain('did not become ready');
  });

  it('restarts after crash with exponential backoff', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: true, owned: true })
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: true, owned: true });
    const process = new MockManagedProcess();
    const processSpawner = new MockProcessSpawner();
    processSpawner.spawn.mockReturnValue(process);
    const { service } = createService({ healthChecker, processSpawner });

    await service.ensureConnectorRunning();
    process.emitExit(1);

    expect(service.getStatus().state).toBe('reconnecting');
    await vi.advanceTimersByTimeAsync(100);
    expect(processSpawner.spawn).toHaveBeenCalledTimes(2);
  });

  it('stops managed connector gracefully', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails
      .mockResolvedValueOnce({ ready: false, owned: false })
      .mockResolvedValueOnce({ ready: true, owned: true })
      .mockResolvedValueOnce({ ready: false, owned: false });
    const process = new MockManagedProcess();
    const processSpawner = new MockProcessSpawner();
    processSpawner.spawn.mockReturnValue(process);
    const { service } = createService({ healthChecker, processSpawner });

    await service.ensureConnectorRunning();
    const status = await service.stopConnector();

    expect(process.kill).toHaveBeenCalledWith('SIGTERM');
    expect(status.state).toBe('disconnected');
    expect(status.managedByDesktop).toBe(false);
  });

  it('records health transitions in structured logs', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails.mockResolvedValue({ ready: true, owned: true });
    const { service, logService } = createService({ healthChecker });

    await service.ensureConnectorRunning();

    const entries = logService.getEntries().map((entry) => entry.message);
    expect(entries.some((message) => message.includes('[lifecycle:health_transition]'))).toBe(true);
  });

  it('blocks connector spawn when packaged runtime integrity failed', async () => {
    const onDiagnostic = vi.fn();
    const healthChecker = new MockHealthChecker();
    const processSpawner = new MockProcessSpawner();
    const logService = new LogService();
    const service = new ConnectorLifecycleService({
      config: {
        ...baseConfig,
        connectorExecutable: '',
        packagedRuntimeIntegrityCategory: 'hash_mismatch',
      },
      processSpawner,
      healthChecker,
      logService,
      sleep: async () => undefined,
      onDiagnostic,
    });

    const status = await service.ensureConnectorRunning();

    expect(status.state).toBe('failed');
    expect(status.lastError).toContain('integrity verification');
    expect(processSpawner.spawn).not.toHaveBeenCalled();
    expect(onDiagnostic).toHaveBeenCalledWith('packaged_runtime_integrity_failure', { category: 'hash_mismatch' });
  });

  it('does not retry reconnect when runtime integrity is blocked', async () => {
    const processSpawner = new MockProcessSpawner();
    const { service } = createService({
      processSpawner,
      config: {
        connectorExecutable: '',
        packagedRuntimeIntegrityCategory: 'missing_manifest',
        autoStart: true,
      },
    });

    await service.initialize();
    await vi.advanceTimersByTimeAsync(60_000);

    expect(processSpawner.spawn).not.toHaveBeenCalled();
    expect(service.getStatus().state).toBe('failed');
  });

  it('fails after max restart attempts', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails.mockResolvedValue({ ready: false, owned: false });
    const process = new MockManagedProcess();
    const processSpawner = new MockProcessSpawner();
    processSpawner.spawn.mockReturnValue(process);
    const { service } = createService({
      healthChecker,
      processSpawner,
      config: { maxRestartAttempts: 1, reconnectBaseDelayMs: 50, startupTimeoutMs: 100 },
      sleep: async () => undefined,
    });

    await service.ensureConnectorRunning();
    process.emitExit(1);
    await vi.advanceTimersByTimeAsync(50);

    expect(service.getStatus().state).toBe('failed');
  });
});

describe('ConnectorLifecycleService health monitoring', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('transitions to disconnected when health fails and auto start is disabled', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails.mockResolvedValue({ ready: false, owned: false });
    const { service } = createService({ healthChecker, config: { autoStart: false } });

    await service.initialize();

    expect(service.getStatus().state).toBe('disconnected');
  });

  it('updates last successful health check timestamp', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealthDetails
      .mockResolvedValueOnce({ ready: true, owned: true })
      .mockResolvedValue({ ready: true, owned: true });
    const { service } = createService({ healthChecker });

    await service.ensureConnectorRunning();

    expect(service.getStatus().lastSuccessfulHealthCheck).not.toBeNull();
  });
});
