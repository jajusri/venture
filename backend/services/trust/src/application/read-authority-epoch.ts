import type { TrustAuthoritySnapshotReader } from '../../../relay/src/application/pilot-authority-verifier.js';

/**
 * The one read Android's local commercial-authority verifier needs to detect a STALE credential
 * (one whose embedded `authorityEpoch` no longer matches live authority) without re-deriving any of
 * Relay's own snapshot logic: reuses the EXACT SAME `TrustAuthoritySnapshotReader` Relay's own
 * pilot verifier already reads from (`PostgresTrustAuthoritySnapshotReader` -- same "current device
 * key" selection rule, same atomic snapshot semantics), so there is exactly one implementation of
 * "what is the current authoritative epoch," not two that could drift.
 *
 * Deliberately narrow: returns ONLY the epoch number, never full membership/device rows -- nothing
 * here is more sensitive than the verification-keys endpoint's own already-public material, and
 * exposing anything more would grow this into a second, parallel authority-read surface.
 */
export interface AuthorityEpochQuery { readonly businessId: string; readonly membershipId: string; readonly deviceId: string }

export class ReadCurrentAuthorityEpoch {
  constructor(private readonly snapshots: TrustAuthoritySnapshotReader) {}

  async execute(input: AuthorityEpochQuery): Promise<number | null> {
    const snapshot = await this.snapshots.read(input.businessId, input.deviceId);
    if (snapshot.businessStatus !== 'active') return null;
    if (!snapshot.membership || snapshot.membership.membershipId !== input.membershipId || snapshot.membership.status !== 'active') return null;
    if (!snapshot.device || snapshot.device.status !== 'active') return null;
    return snapshot.membership.authorityEpoch.value;
  }
}
