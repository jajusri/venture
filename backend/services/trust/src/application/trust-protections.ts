export type ProtectedTrustAction = 'credential_issuance' | 'device_registration' | 'failed_verification' | 'authority_change';
export interface TrustRateLimitRequest { readonly action: ProtectedTrustAction; readonly businessId: string; readonly actorId?: string; readonly deviceId?: string; readonly now: Date }
export interface PartitionedTrustRateLimiter { consume(request: TrustRateLimitRequest): Promise<{ readonly allowed: boolean; readonly retryAfterMs?: number }> }
export interface ReplayEvidenceStore { reserve(partitionKey: string, idempotencyKey: string, expiresAt: Date): Promise<'reserved' | 'duplicate'> }
export interface TrustSecurityAuditSink { record(event: { readonly kind: string; readonly businessId: string; readonly actorId?: string; readonly deviceId?: string; readonly occurredAt: Date }): Promise<void> }

export class TrustProtectionGate {
  constructor(private readonly limiter: PartitionedTrustRateLimiter, private readonly replays: ReplayEvidenceStore, private readonly audit: TrustSecurityAuditSink) {}
  async authorize(request: TrustRateLimitRequest, idempotencyKey?: string): Promise<'allowed' | 'duplicate'> {
    if (!request.businessId.trim()) throw new Error('Business partition is required');
    const decision = await this.limiter.consume(request);
    if (!decision.allowed) { await this.audit.record({ kind: `rate_limited_${request.action}`, businessId: request.businessId, ...(request.actorId ? { actorId: request.actorId } : {}), ...(request.deviceId ? { deviceId: request.deviceId } : {}), occurredAt: request.now }); throw new Error('Trust action rate limited'); }
    if (!idempotencyKey) return 'allowed';
    const partition = [request.businessId, request.actorId ?? '-', request.deviceId ?? '-', request.action].join(':');
    return await this.replays.reserve(partition, idempotencyKey, new Date(request.now.getTime() + 86_400_000)) === 'duplicate' ? 'duplicate' : 'allowed';
  }
}
