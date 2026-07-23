/** Generic ERP transport request — extensible for future non-Tally connectors. */
export interface ErpTransportRequest {
  readonly body: string;
  readonly contentType: string;
  readonly timeoutMs?: number;
  readonly correlationId?: string;
  readonly signal?: AbortSignal;
}

/** Raw ERP transport response before domain parsing. */
export interface ErpTransportResponse {
  readonly body: string;
  readonly statusCode: number;
  readonly durationMs: number;
  readonly headers: Readonly<Record<string, string>>;
}

/** Connection lifecycle states for offline-first reconnect behaviour. */
export type TallyConnectionState =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'degraded'
  | 'reconnecting';

export interface TallyRequestMetadata {
  readonly requestId: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly sentAt: string;
}

export interface TallyResponseMetadata {
  readonly requestId: string;
  readonly receivedAt: string;
  readonly durationMs: number;
  readonly statusCode: number;
  readonly byteLength: number;
}

export interface TallyExchangeResult {
  readonly request: TallyRequestMetadata;
  readonly response: TallyResponseMetadata;
  readonly rawXml: string;
}

/** Discovered company summary — structural metadata only, no ledger/voucher data. */
export interface DiscoveredCompany {
  readonly id: string;
  readonly name: string;
  readonly startingFrom?: string;
  readonly booksFrom?: string;
  readonly baseCurrency?: string;
}

export interface TallyLastRequestSnapshot {
  readonly correlationId: string;
  readonly collectionId?: string;
  readonly reportId?: string;
  readonly sentAt: string;
  readonly outcome?: string;
}

export interface TallyDiagnosticsSnapshot {
  readonly state: TallyConnectionState;
  readonly host: string;
  readonly port: number;
  readonly lastSuccessfulPingAt?: string;
  readonly lastErrorAt?: string;
  readonly lastErrorCode?: string;
  readonly lastErrorMessage?: string;
  readonly totalRequests: number;
  readonly failedRequests: number;
  readonly reconnectAttempts: number;
  readonly averageLatencyMs: number;
  readonly poolActiveConnections: number;
  readonly poolWaitingRequests: number;
  readonly safeMode: boolean;
  readonly circuitState: 'closed' | 'open' | 'half_open';
  readonly lastRequest?: TallyLastRequestSnapshot;
  readonly runtimeLimits: {
    readonly poolMaxConnections: number;
    readonly retryMaxAttempts: number;
    readonly minRequestIntervalMs: number;
    readonly maxRequestBytes: number;
    readonly maxResponseBytes: number;
    readonly circuitBreakerEnabled: boolean;
    readonly timeoutMs: number;
  };
}
