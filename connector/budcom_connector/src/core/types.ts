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
  /** True when LAN business routes are protected by either supported device-auth policy. */
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
  /**
   * Authoritative server-side clock reading at report time (epoch millis). MVP-1.4 Catalogue
   * (docs/architecture/BUDCOM-MVP-1-4-CATALOGUE-ARCHITECTURE.md §15) needs a non-device-local
   * timestamp source for publication/conflict decisions; this Connector process (running on the
   * business's own Desktop) is the closest thing to a "server" in this LAN-local architecture, and
   * already stamps ISO timestamps elsewhere (sync runs, pairing sessions). Reusing the existing
   * `/health` endpoint rather than adding a new route.
   */
  readonly serverTimeEpochMillis: number;
}

export interface ReadinessReport {
  readonly status: 'ready' | 'not_ready';
  readonly repositoryAvailable: boolean;
  readonly databaseAccessible: boolean;
  readonly voucherSynchronizationComposed: boolean;
  readonly voucherApplicationComposed: boolean;
}
