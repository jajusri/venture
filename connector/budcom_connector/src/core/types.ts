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
  readonly services: readonly ServiceStatus[];
}
