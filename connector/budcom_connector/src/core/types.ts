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
  /** True only when networkExposure is 'lan' and requireDeviceAuthForLan is enabled. */
  readonly authenticatedLanAccessEnabled: boolean;
  /** Stable Connector identity — see docs/architecture. Not a secret; same trust level as a hostname. */
  readonly connectorId: string;
  /** User-friendly Desktop/Connector name, shown by Android instead of a raw IP. */
  readonly connectorName: string;
  /** True once the mDNS advertiser (_budcom._tcp.local) is actively publishing. */
  readonly discoveryAdvertising: boolean;
  readonly services: readonly ServiceStatus[];
  /** Present when desktop supervisor supplied BUDCOM_STARTUP_CORRELATION_ID for this launch. */
  readonly startupCorrelationId?: string | null;
  readonly repositoryAvailable: boolean;
  readonly databaseAccessible: boolean;
  /** ISO timestamp this Connector process actually started, derived from process.uptime(). */
  readonly processStartedAt: string;
}

export interface ReadinessReport {
  readonly status: 'ready' | 'not_ready';
  readonly repositoryAvailable: boolean;
  readonly databaseAccessible: boolean;
  readonly voucherSynchronizationComposed: boolean;
  readonly voucherApplicationComposed: boolean;
}
