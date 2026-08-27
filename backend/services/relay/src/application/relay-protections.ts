export interface RelayIngressLimitRequest {
  readonly senderBusinessId: string;
  readonly mailboxId: string;
  readonly now: Date;
}

export interface RelayIngressLimiter {
  consume(request: RelayIngressLimitRequest): Promise<{ readonly allowed: boolean; readonly retryAfterMs?: number }>;
}

export class PartitionedRelayIngressLimiter implements RelayIngressLimiter {
  private readonly windows = new Map<string, { count: number; resetAt: number }>();

  constructor(
    private readonly maxRequestsPerWindow: number,
    private readonly windowMs: number,
  ) {
    if (maxRequestsPerWindow < 1) throw new Error('Relay ingress window must allow at least one request');
    if (windowMs < 1) throw new Error('Relay ingress window must be positive');
  }

  consume(request: RelayIngressLimitRequest): Promise<{ readonly allowed: boolean; readonly retryAfterMs?: number }> {
    const partition = `${request.senderBusinessId}:${request.mailboxId}`;
    const now = request.now.getTime();
    const current = this.windows.get(partition);
    if (!current || now >= current.resetAt) {
      this.windows.set(partition, { count: 1, resetAt: now + this.windowMs });
      return Promise.resolve({ allowed: true });
    }
    if (current.count >= this.maxRequestsPerWindow) {
      return Promise.resolve({ allowed: false, retryAfterMs: Math.max(1, current.resetAt - now) });
    }
    current.count += 1;
    return Promise.resolve({ allowed: true });
  }
}
