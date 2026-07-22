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
}

const baseConfig: ConnectorLifecycleConfig = {
  connectorBaseUrl: 'http://localhost:8080',
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
    healthChecker.checkHealth.mockResolvedValue(true);
    const { service, processSpawner } = createService({ healthChecker });

    const status = await service.ensureConnectorRunning();

    expect(status.state).toBe('connected');
    expect(status.externalProcessDetected).toBe(true);
    expect(status.managedByDesktop).toBe(false);
    expect(processSpawner.spawn).not.toHaveBeenCalled();
  });

  it('starts managed connector when health is unavailable', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealth
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);
    const { service, processSpawner } = createService({ healthChecker });

    const status = await service.ensureConnectorRunning();

    expect(processSpawner.spawn).toHaveBeenCalledTimes(1);
    expect(status.managedByDesktop).toBe(true);
    expect(status.state).toBe('connected');
  });

  it('prevents duplicate launch while managed process exists', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealth
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true)
      .mockResolvedValueOnce(false);
    const { service, processSpawner } = createService({ healthChecker });

    await service.ensureConnectorRunning();
    await service.ensureConnectorRunning();

    expect(processSpawner.spawn).toHaveBeenCalledTimes(1);
  });

  it('handles startup timeout', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealth.mockResolvedValue(false);
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
    healthChecker.checkHealth
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true)
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);
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
    healthChecker.checkHealth.mockResolvedValueOnce(false).mockResolvedValueOnce(true);
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
    healthChecker.checkHealth.mockResolvedValue(true);
    const { service, logService } = createService({ healthChecker });

    await service.ensureConnectorRunning();

    const entries = logService.getEntries().map((entry) => entry.message);
    expect(entries.some((message) => message.includes('[lifecycle:health_transition]'))).toBe(true);
  });

  it('fails after max restart attempts', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealth.mockResolvedValue(false);
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
    healthChecker.checkHealth.mockResolvedValue(false);
    const { service } = createService({ healthChecker, config: { autoStart: false } });

    await service.initialize();

    expect(service.getStatus().state).toBe('disconnected');
  });

  it('updates last successful health check timestamp', async () => {
    const healthChecker = new MockHealthChecker();
    healthChecker.checkHealth.mockResolvedValue(true);
    const { service } = createService({ healthChecker });

    await service.ensureConnectorRunning();

    expect(service.getStatus().lastSuccessfulHealthCheck).not.toBeNull();
  });
});
