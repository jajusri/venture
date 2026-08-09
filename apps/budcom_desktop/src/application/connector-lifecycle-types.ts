export type ConnectorLifecycleState =
  | 'starting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'failed';

export type ConnectorLifecycleStateLabel =
  | 'Starting'
  | 'Connected'
  | 'Reconnecting'
  | 'Disconnected'
  | 'Failed';

export type LifecycleLogEvent =
  | 'connector_started'
  | 'connector_stopped'
  | 'connector_restarted'
  | 'startup_failure'
  | 'crash_detected'
  | 'restart_attempt'
  | 'health_transition'
  | 'process_exit'
  | 'retry_attempt'
  | 'external_process_detected';

export interface ConnectorLifecycleConfig {
  readonly connectorBaseUrl: string;
  readonly connectorBindMode: 'local-only' | 'trusted-lan';
  readonly connectorHost: string;
  readonly connectorPort: number;
  readonly connectorExecutable: string;
  readonly connectorArgs: readonly string[];
  readonly connectorCwd: string;
  readonly childEnv?: Readonly<Record<string, string | undefined>>;
  readonly isPackaged?: boolean;
  readonly packagedRuntimeIntegrityCategory?: string | null;
  /** Clear, actionable error when development-mode Node runtime resolution failed. */
  readonly developmentRuntimeError?: string | null;
  readonly autoStart: boolean;
  readonly healthPollIntervalMs: number;
  readonly startupTimeoutMs: number;
  readonly shutdownGraceMs: number;
  readonly maxRestartAttempts: number;
  readonly reconnectBaseDelayMs: number;
  readonly staleHealthThresholdMs: number;
  readonly startupCorrelationId?: string | null;
  /** Label from packaged VERSION.txt (or null when unavailable). */
  readonly bundledConnectorVersion?: string | null;
}

export interface ConnectorLifecycleStatus {
  readonly state: ConnectorLifecycleState;
  readonly stateLabel: ConnectorLifecycleStateLabel;
  readonly managedByDesktop: boolean;
  readonly externalProcessDetected: boolean;
  readonly lastSuccessfulHealthCheck: string | null;
  readonly lastError: string | null;
  readonly restartAttempts: number;
  readonly processExitCode: number | null;
  readonly connectorExecutable: string;
  readonly connectorPort: number;
  readonly userMessage: string | null;
  readonly managedProcessPid: number | null;
  readonly bundledConnectorVersion: string | null;
}

export interface SpawnSpec {
  readonly command: string;
  readonly args: readonly string[];
  readonly cwd: string;
  readonly env?: Record<string, string>;
}

export interface ProcessDiagnostic {
  readonly stream: 'stderr';
  readonly text: string;
}

export interface ManagedProcess {
  readonly pid: number;
  kill(signal?: NodeJS.Signals): Promise<void>;
  onExit(listener: (code: number | null, signal: NodeJS.Signals | null) => void): void;
}

export interface ProcessSpawner {
  spawn(spec: SpawnSpec): ManagedProcess;
}

export interface HealthCheckDetails {
  readonly ready: boolean;
  readonly owned: boolean;
  readonly bindPort?: number;
  readonly startupCorrelationId?: string | null;
  /** Actual bind host the running Connector reports (runtime integrity verification). */
  readonly bindHost?: string;
  readonly networkExposure?: 'loopback' | 'lan';
  readonly connectorId?: string;
  readonly connectorVersion?: string;
  readonly processStartedAt?: string;
}

/**
 * Fail-closed runtime-integrity blocked states. Distinct from
 * `packagedRuntimeIntegrityCategory` (packaged Node runtime binary hash check, evaluated before
 * any spawn): these are only knowable after the Connector has actually reported its own health,
 * so they gate `markHealthy()`/`markExternalRunning()` rather than the pre-spawn path.
 */
export type BindIntegrityCategory = 'CONNECTOR_BIND_MISMATCH' | 'PACKAGED_CONNECTOR_MISMATCH';

export interface HealthChecker {
  checkHealth(): Promise<boolean>;
  checkHealthDetails(): Promise<HealthCheckDetails>;
}
