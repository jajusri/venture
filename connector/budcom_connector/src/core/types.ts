export interface ServiceLifecycle {
  start(): Promise<void>;
  stop(): Promise<void>;
  isRunning(): boolean;
}

export interface ServiceStatus {
  readonly name: string;
  readonly running: boolean;
  readonly ready: boolean;
  readonly message?: string;
}

export interface HealthReport {
  readonly status: 'ok' | 'degraded' | 'unavailable';
  readonly schemaVersion: string;
  readonly connectorVersion: string;
  readonly tallyReachable: boolean;
  readonly readOnly: true;
  readonly bindHost: string;
  readonly bindPort: number;
  readonly networkExposure: 'loopback' | 'lan';
  readonly networkExposureWarning: string | null;
  readonly networkPolicySatisfied: boolean;
  /** Reserved for future authenticated LAN access; always false today. */
  readonly authenticatedLanAccessEnabled: false;
  readonly services: readonly ServiceStatus[];
  /** Present when desktop supervisor supplied BUDCOM_STARTUP_CORRELATION_ID for this launch. */
  readonly startupCorrelationId?: string | null;
  readonly repositoryAvailable: boolean;
  readonly databaseAccessible: boolean;
}

export interface ReadinessReport {
  readonly status: 'ready' | 'not_ready';
  readonly repositoryAvailable: boolean;
  readonly databaseAccessible: boolean;
  readonly voucherSynchronizationComposed: boolean;
  readonly voucherApplicationComposed: boolean;
}
