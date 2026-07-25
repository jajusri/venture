import { ConnectorHttpClient } from './connector-http-client.js';
import type {
  ConnectorLifecycleConfig,
  ConnectorLifecycleState,
  ConnectorLifecycleStatus,
  HealthChecker,
  LifecycleLogEvent,
  ManagedProcess,
  ProcessSpawner,
} from './connector-lifecycle-types.js';
import {
  mapLifecycleStateLabel,
  mapLifecycleUserMessage,
  mapSpawnError,
} from './lifecycle-error-mapper.js';
import type { LogService } from './log-service.js';
import { validateConnectorExecutable } from './connector-lifecycle-config.js';

export class HttpHealthChecker implements HealthChecker {
  private readonly client: ConnectorHttpClient;

  constructor(baseUrl: string, fetchImpl?: typeof fetch) {
    this.client = new ConnectorHttpClient({
      baseUrl,
      fetchImpl,
      maxAttempts: 1,
      timeoutMs: 3_000,
    });
  }

  async checkHealth(): Promise<boolean> {
    return this.client.isReachable();
  }
}

export interface ConnectorLifecycleServiceOptions {
  readonly config: ConnectorLifecycleConfig;
  readonly processSpawner: ProcessSpawner;
  readonly healthChecker: HealthChecker;
  readonly logService: LogService;
  readonly sleep?: (ms: number) => Promise<void>;
}

export class ConnectorLifecycleService {
  private readonly config: ConnectorLifecycleConfig;
  private readonly processSpawner: ProcessSpawner;
  private readonly healthChecker: HealthChecker;
  private readonly logService: LogService;
  private readonly sleep: (ms: number) => Promise<void>;

  private state: ConnectorLifecycleState = 'disconnected';
  private managedByDesktop = false;
  private externalProcessDetected = false;
  private managedProcess: ManagedProcess | null = null;
  private lastSuccessfulHealthCheck: string | null = null;
  private lastError: string | null = null;
  private restartAttempts = 0;
  private processExitCode: number | null = null;
  private healthTimer: NodeJS.Timeout | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private startupInProgress = false;
  private stopping = false;
  private onStatusChanged: (() => void) | null = null;

  constructor(options: ConnectorLifecycleServiceOptions) {
    this.config = options.config;
    this.processSpawner = options.processSpawner;
    this.healthChecker = options.healthChecker;
    this.logService = options.logService;
    this.sleep = options.sleep ?? ((ms: number) => new Promise((resolve) => setTimeout(resolve, ms)));
  }

  setStatusListener(listener: (() => void) | null): void {
    this.onStatusChanged = listener;
  }

  getStatus(): ConnectorLifecycleStatus {
    return {
      state: this.state,
      stateLabel: mapLifecycleStateLabel(this.state),
      managedByDesktop: this.managedByDesktop,
      externalProcessDetected: this.externalProcessDetected,
      lastSuccessfulHealthCheck: this.lastSuccessfulHealthCheck,
      lastError: this.lastError,
      restartAttempts: this.restartAttempts,
      processExitCode: this.processExitCode,
      connectorExecutable: this.config.connectorExecutable,
      connectorPort: this.config.connectorPort,
      userMessage: this.lastError,
      managedProcessPid: this.managedProcess?.pid ?? null,
    };
  }

  async initialize(): Promise<void> {
    this.startHealthMonitoring();
    if (this.config.autoStart) {
      await this.ensureConnectorRunning();
    } else {
      await this.refreshHealthState();
    }
  }

  async ensureConnectorRunning(): Promise<ConnectorLifecycleStatus> {
    if (this.managedProcess && this.managedByDesktop) {
      if (await this.healthChecker.checkHealth()) {
        this.markHealthy();
        return this.getStatus();
      }
    }

    if (await this.healthChecker.checkHealth()) {
      if (!this.managedByDesktop) {
        this.markExternalRunning();
      } else {
        this.markHealthy();
      }
      return this.getStatus();
    }

    if (this.startupInProgress || this.managedProcess) {
      return this.getStatus();
    }

    return this.startManagedConnector();
  }

  async stopConnector(): Promise<ConnectorLifecycleStatus> {
    this.clearReconnectTimer();
    this.stopping = true;
    try {
      if (!this.managedProcess) {
        if (await this.healthChecker.checkHealth()) {
          this.lastError = mapLifecycleUserMessage('ALREADY_RUNNING');
          this.logLifecycle('connector_stopped', 'Stop requested but connector is managed externally.');
          this.externalProcessDetected = true;
          this.transitionState('connected');
          return this.getStatus();
        }
        this.transitionState('disconnected');
        return this.getStatus();
      }

      await this.managedProcess.kill('SIGTERM');
      await this.sleep(this.config.shutdownGraceMs);
      this.managedProcess = null;
      this.managedByDesktop = false;
      await this.waitForHealthDown(Math.min(this.config.startupTimeoutMs, 10_000));
      this.transitionState('disconnected');
      this.logLifecycle('connector_stopped', 'Connector stopped by desktop supervisor.');
      return this.getStatus();
    } catch {
      this.lastError = mapLifecycleUserMessage('STOP_FAILED');
      this.transitionState('failed');
      return this.getStatus();
    } finally {
      this.stopping = false;
    }
  }

  async restartConnector(): Promise<ConnectorLifecycleStatus> {
    this.logLifecycle('connector_restarted', 'Manual connector restart requested.');
    if (this.externalProcessDetected && !this.managedByDesktop) {
      this.lastError = mapLifecycleUserMessage('ALREADY_RUNNING');
      return this.getStatus();
    }

    await this.stopConnector();

    if (await this.healthChecker.checkHealth()) {
      this.markExternalRunning();
      this.lastError = mapLifecycleUserMessage('ALREADY_RUNNING');
      return this.getStatus();
    }

    this.restartAttempts = 0;
    this.externalProcessDetected = false;
    return this.startManagedConnector();
  }

  async shutdown(): Promise<void> {
    this.stopHealthMonitoring();
    this.clearReconnectTimer();
    if (this.managedByDesktop && this.managedProcess) {
      await this.stopConnector();
    }
  }

  startHealthMonitoring(): void {
    if (this.healthTimer) {
      clearInterval(this.healthTimer);
    }
    this.healthTimer = setInterval(() => {
      void this.refreshHealthState();
    }, this.config.healthPollIntervalMs);
  }

  stopHealthMonitoring(): void {
    if (this.healthTimer) {
      clearInterval(this.healthTimer);
      this.healthTimer = null;
    }
  }

  private async startManagedConnector(): Promise<ConnectorLifecycleStatus> {
    const validationError = validateConnectorExecutable(this.config);
    if (validationError) {
      this.lastError = validationError;
      this.transitionState('failed');
      this.logLifecycle('startup_failure', validationError);
      return this.getStatus();
    }

    this.startupInProgress = true;
    this.transitionState('starting');
    this.lastError = null;

    try {
      const process = this.processSpawner.spawn({
        command: this.config.connectorExecutable,
        args: this.config.connectorArgs,
        cwd: this.config.connectorCwd,
        env: {
          ...(this.config.childEnv ?? {}),
          BUDCOM_CONNECTOR_HOST: this.config.connectorHost,
          BUDCOM_CONNECTOR_PORT: String(this.config.connectorPort),
        },
      });

      this.managedProcess = process;
      this.managedByDesktop = true;
      this.externalProcessDetected = false;
      this.logLifecycle('connector_started', `Connector started with PID ${process.pid}.`);

      process.onExit((code, signal) => {
        void this.handleProcessExit(code, signal);
      });

      const ready = await this.waitForHealth(this.config.startupTimeoutMs);
      if (!ready) {
        this.lastError = mapLifecycleUserMessage('STARTUP_TIMEOUT');
        this.transitionState('failed');
        this.logLifecycle('startup_failure', this.lastError);
        return this.getStatus();
      }

      this.restartAttempts = 0;
      this.markHealthy();
      return this.getStatus();
    } catch (error) {
      const mapped = mapSpawnError(error);
      this.lastError = mapped.message;
      this.transitionState('failed');
      this.logLifecycle('startup_failure', mapped.message);
      return this.getStatus();
    } finally {
      this.startupInProgress = false;
    }
  }

  private async handleProcessExit(
    code: number | null,
    signal: NodeJS.Signals | null,
  ): Promise<void> {
    if (this.stopping) {
      return;
    }

    this.processExitCode = code;
    this.managedProcess = null;
    this.managedByDesktop = false;
    this.logLifecycle(
      'process_exit',
      `Connector process exited code=${code ?? 'null'} signal=${signal ?? 'null'}.`,
    );
    this.logLifecycle('crash_detected', mapLifecycleUserMessage('PROCESS_CRASH'));

    if (this.restartAttempts >= this.config.maxRestartAttempts) {
      this.lastError = mapLifecycleUserMessage('MAX_RESTARTS');
      this.transitionState('failed');
      return;
    }

    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    this.clearReconnectTimer();
    this.restartAttempts += 1;
    const delay = this.config.reconnectBaseDelayMs * this.restartAttempts;
    this.transitionState('reconnecting');
    this.logLifecycle('restart_attempt', `Restart attempt ${this.restartAttempts} in ${delay}ms.`);
    this.logLifecycle('retry_attempt', `Retry attempt ${this.restartAttempts}.`);

    this.reconnectTimer = setTimeout(() => {
      void this.ensureConnectorRunning();
    }, delay);
  }

  private async refreshHealthState(): Promise<void> {
    if (this.startupInProgress) {
      return;
    }

    const healthy = await this.healthChecker.checkHealth();
    if (healthy) {
      this.markHealthy();
      return;
    }

    const stale =
      this.lastSuccessfulHealthCheck !== null &&
      Date.now() - new Date(this.lastSuccessfulHealthCheck).getTime() > this.config.staleHealthThresholdMs;

    if ((this.state === 'connected' && stale) || this.state === 'connected') {
      this.transitionState('reconnecting');
      if (this.managedByDesktop && !this.managedProcess) {
        this.scheduleReconnect();
      } else if (!this.managedByDesktop && this.config.autoStart) {
        this.scheduleReconnect();
      } else {
        this.transitionState('disconnected');
      }
      return;
    }

    if (this.state !== 'failed' && this.state !== 'reconnecting') {
      this.transitionState('disconnected');
    }
  }

  private markExternalRunning(): void {
    this.externalProcessDetected = true;
    this.managedByDesktop = false;
    this.lastError = null;
    this.markHealthy();
    this.logLifecycle('external_process_detected', 'Existing connector process detected via /health.');
  }

  private markHealthy(): void {
    this.lastSuccessfulHealthCheck = new Date().toISOString();
    this.lastError = null;
    this.transitionState('connected');
  }

  private async waitForHealth(timeoutMs: number): Promise<boolean> {
    const attempts = Math.max(1, Math.ceil(timeoutMs / 500));
    for (let attempt = 0; attempt < attempts; attempt += 1) {
      if (await this.healthChecker.checkHealth()) {
        return true;
      }
      await this.sleep(500);
    }
    return false;
  }

  private async waitForHealthDown(timeoutMs: number): Promise<boolean> {
    const attempts = Math.max(1, Math.ceil(timeoutMs / 500));
    for (let attempt = 0; attempt < attempts; attempt += 1) {
      if (!(await this.healthChecker.checkHealth())) {
        return true;
      }
      await this.sleep(500);
    }
    return false;
  }

  private transitionState(next: ConnectorLifecycleState): void {
    if (this.state === next) {
      return;
    }
    const previous = this.state;
    this.state = next;
    this.logLifecycle('health_transition', `${previous} -> ${next}`);
    this.notifyStatusChanged();
  }

  private logLifecycle(event: LifecycleLogEvent, message: string): void {
    const level = event === 'startup_failure' || event === 'crash_detected' ? 'error' : 'information';
    if (event === 'retry_attempt' || event === 'restart_attempt') {
      this.logService.append('warning', `[lifecycle:${event}] ${message}`);
      return;
    }
    this.logService.append(level, `[lifecycle:${event}] ${message}`);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private notifyStatusChanged(): void {
    this.onStatusChanged?.();
  }
}
