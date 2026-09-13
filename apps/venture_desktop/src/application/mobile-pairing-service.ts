import QRCode from 'qrcode';

import { ConnectorHttpClient, DESKTOP_CONTROL_TOKEN_HEADER } from './connector-http-client.js';
import type { ConnectorLifecycleStatus } from './connector-lifecycle-types.js';
import type { ActiveNetworkAdapter, ConnectorBindModeForEndpoint, TrustedLanEligibility } from './network/active-network-resolver.js';
import { resolveMobileEndpointHost } from './network/active-network-resolver.js';
import type {
  ActivePairingSessionView,
  PairingSessionCreateResult,
  SecurePairingCapability,
  SettingsMutationResult,
  TrustedPairingDeviceSummary,
} from './types.js';

export type { SettingsMutationResult } from './types.js';

export interface MobilePairingServiceOptions {
  readonly getConnectorBaseUrl: () => string;
  readonly getSecureMobilePairingEnabled: () => boolean;
  readonly setSecureMobilePairingEnabled: (enabled: boolean) => SettingsMutationResult;
  /**
   * Token for the CURRENTLY managed+running Connector child process, or null when none is
   * active/managed/pairing-enabled. Desktop main process only — see desktop-control-token.ts.
   * Never exposed to this service's callers (IPC handlers must never forward it).
   */
  readonly getControlToken: () => string | null;
  readonly getLifecycleStatus: () => ConnectorLifecycleStatus;
  /**
   * The SAME live network/bind-mode sources MobileAccessStatusService reads — see
   * resolveMobileEndpointHost, the one authoritative "is this Connector reachable by another
   * device on the LAN, and at what address?" answer both services consume. Required (not
   * optional): a pairing session must never be offered as 'ready' without this check, since an
   * unchecked Local-only/unresolved-network Connector would otherwise let the QR silently embed
   * a loopback host (see TD-012, docs/technical-debt/registry.md).
   */
  readonly getConnectorBindMode: () => ConnectorBindModeForEndpoint;
  readonly getActiveNetwork: () => ActiveNetworkAdapter | null;
  readonly getTrustedLanEligibility: () => TrustedLanEligibility;
  readonly fetchImpl?: typeof fetch;
  readonly requestTimeoutMs?: number;
}

/**
 * Pure function, exported for direct unit testing (avoids having to decode a rendered QR
 * image to verify payload contents). Never includes a permanent device credential, control
 * token, or private key — only what the QR-path redeem route
 * (connector/venture_connector/src/api/routes/pairing.ts) accepts, plus the transport-pinning
 * fields when secure transport is enabled.
 */
export function buildPairingQrPayload(created: PairingSessionCreateResult): Record<string, unknown> {
  const payload: Record<string, unknown> = {
    schemaVersion: created.schemaVersion,
    pairingSessionId: created.pairingSessionId,
    secret: created.secret,
    connectorId: created.connectorId,
    connectorName: created.connectorName,
    host: created.host,
    port: created.port,
    expiresAt: created.expiresAt,
  };
  if (created.transportProtocol) {
    payload.transportProtocol = created.transportProtocol;
    payload.securePort = created.securePort;
    payload.transportFingerprint = created.transportFingerprint;
    payload.fingerprintAlgorithm = created.fingerprintAlgorithm;
    payload.transportIdentityVersion = created.transportIdentityVersion;
  }
  return payload;
}

type SessionInternalState =
  | { readonly kind: 'idle' }
  | { readonly kind: 'creating' }
  | {
      readonly kind: 'active';
      readonly pairingSessionId: string;
      readonly shortCode: string;
      readonly expiresAt: string;
      readonly qrDataUrl: string;
    }
  | { readonly kind: 'redeemed'; readonly deviceLabel: string | null }
  | { readonly kind: 'expired' }
  | { readonly kind: 'cancelled' }
  | { readonly kind: 'failed'; readonly message: string };

/**
 * Desktop-main-process-only pairing controller. Every Connector control request originates
 * here, with the Desktop control token attached automatically (never accepted from a caller —
 * see ipc-allowlist.ts, which has no channel that takes a token parameter). Renderer-facing
 * methods return only sanitized DTOs (ActivePairingSessionView, TrustedPairingDeviceSummary,
 * SecurePairingCapability) — never the raw QR secret, short code, control token, or a permanent
 * device credential.
 *
 * The QR secret necessarily exists in memory here between session creation and its natural
 * disappearance (expiry, cancellation, redemption, or a new startPairing() superseding it) — it
 * is never logged, never persisted, and never included in the DTOs returned to the renderer
 * (only the rendered QR image data URL is, which is a one-way encoding, not the raw payload).
 */
export class MobilePairingService {
  private sessionState: SessionInternalState = { kind: 'idle' };
  /** Bumped on every startPairing()/cancelPairing() call so a superseded in-flight request's
   *  result is discarded rather than clobbering a newer one (see architectural note in
   *  startPairing()). */
  private generation = 0;

  constructor(private readonly options: MobilePairingServiceOptions) {}

  async getSecurePairingCapability(): Promise<SecurePairingCapability> {
    if (!this.options.getSecureMobilePairingEnabled()) {
      return this.capability('disabled', 'Secure mobile pairing is turned off.');
    }

    const token = this.options.getControlToken();
    const lifecycle = this.options.getLifecycleStatus();
    if (!token || !lifecycle.managedByDesktop) {
      return this.capability(
        'restart_required',
        'Restart the Connector for secure mobile pairing to take effect.',
      );
    }
    if (lifecycle.state !== 'connected') {
      return this.capability('unavailable', 'The Connector is not currently connected.');
    }

    const endpointResolution = resolveMobileEndpointHost({
      bindMode: this.options.getConnectorBindMode(),
      activeNetwork: this.options.getActiveNetwork(),
      trustedLanEligibility: this.options.getTrustedLanEligibility(),
    });
    if (!endpointResolution.ready) {
      return this.capability('unavailable', endpointResolution.reason);
    }

    try {
      const client = this.buildControlClient();
      const health = await client.getHealth();
      let trustedDeviceCount: number | null = null;
      try {
        const credentials = await client.listPairingCredentials();
        trustedDeviceCount = credentials.items.filter((item) => item.status === 'active').length;
      } catch {
        trustedDeviceCount = null;
      }
      return {
        state: 'ready',
        connectorName: health.connectorName ?? null,
        transportFingerprint: null,
        trustedDeviceCount,
        userMessage: null,
      };
    } catch (error) {
      return this.capability(
        'unavailable',
        error instanceof Error ? error.message : 'The Connector is not reachable.',
      );
    }
  }

  /**
   * Starts a new pairing session, replacing any prior one. Superseded calls (a second
   * startPairing()/cancelPairing() invoked before this one's network round trip finishes) are
   * detected via the generation counter and discarded — the panel only ever reflects the most
   * recent user action, never a stale response arriving late.
   */
  async startPairing(): Promise<ActivePairingSessionView> {
    this.generation += 1;
    const myGeneration = this.generation;
    this.sessionState = { kind: 'creating' };

    const capability = await this.getSecurePairingCapability();
    if (myGeneration !== this.generation) {
      return this.toView();
    }
    if (capability.state !== 'ready') {
      this.sessionState = { kind: 'failed', message: capability.userMessage ?? 'Secure pairing is not ready.' };
      return this.toView();
    }

    try {
      const client = this.buildControlClient();
      const created = await client.createPairingSession();
      if (myGeneration !== this.generation) {
        return this.toView();
      }
      const qrDataUrl = await this.renderQrDataUrl(created);
      if (myGeneration !== this.generation) {
        return this.toView();
      }
      this.sessionState = {
        kind: 'active',
        pairingSessionId: created.pairingSessionId,
        shortCode: created.shortCode,
        expiresAt: created.expiresAt,
        qrDataUrl,
      };
    } catch (error) {
      if (myGeneration !== this.generation) {
        return this.toView();
      }
      this.sessionState = {
        kind: 'failed',
        message: error instanceof Error ? error.message : 'Failed to start pairing.',
      };
    }
    return this.toView();
  }

  /** Bounded status check — callers (the renderer's own poll loop) decide the cadence; this
   *  method itself never schedules a repeat call. */
  async getActivePairingStatus(): Promise<ActivePairingSessionView> {
    if (this.sessionState.kind !== 'active') {
      return this.toView();
    }
    const pairingSessionId = this.sessionState.pairingSessionId;
    const myGeneration = this.generation;

    try {
      const client = this.buildControlClient();
      const status = await client.getPairingSessionStatus(pairingSessionId);
      if (myGeneration !== this.generation || this.sessionState.kind !== 'active') {
        return this.toView();
      }
      if (status.expired) {
        this.sessionState = { kind: 'expired' };
      } else if (status.cancelled) {
        this.sessionState = { kind: 'cancelled' };
      } else if (status.redeemed) {
        const deviceLabel = await this.tryResolveRecentlyRedeemedDeviceLabel(client);
        if (myGeneration === this.generation) {
          this.sessionState = { kind: 'redeemed', deviceLabel };
        }
      }
    } catch {
      // A single transient poll failure must not flip an otherwise-active session to failed —
      // the next poll tick (or the local expiry countdown) will resolve it.
    }
    return this.toView();
  }

  async cancelPairing(): Promise<ActivePairingSessionView> {
    this.generation += 1;
    if (this.sessionState.kind === 'active') {
      const { pairingSessionId } = this.sessionState;
      this.sessionState = { kind: 'cancelled' };
      try {
        const client = this.buildControlClient();
        await client.cancelPairingSession(pairingSessionId);
      } catch {
        // Already transitioned to cancelled locally — a failed server-side cancel just means the
        // session expires naturally instead. Not surfaced as an error for a UI action the user
        // already sees as completed.
      }
    } else {
      this.sessionState = { kind: 'idle' };
    }
    return this.toView();
  }

  async listTrustedPairingDevices(): Promise<readonly TrustedPairingDeviceSummary[]> {
    try {
      const client = this.buildControlClient();
      const result = await client.listPairingCredentials();
      return result.items.map((item) => ({
        credentialId: item.credentialId,
        deviceLabel: item.deviceLabel,
        firstPairedAt: item.createdAt,
        lastUsedAt: item.lastUsedAt,
        status: item.status,
      }));
    } catch {
      return [];
    }
  }

  async revokeTrustedPairingDevice(credentialId: string): Promise<{ ok: boolean; message: string }> {
    try {
      const client = this.buildControlClient();
      return await client.revokePairingCredential(credentialId);
    } catch (error) {
      return {
        ok: false,
        message: error instanceof Error ? error.message : 'Failed to revoke device.',
      };
    }
  }

  enableSecurePairing(): SettingsMutationResult {
    return this.options.setSecureMobilePairingEnabled(true);
  }

  /**
   * Only ever mutates the persisted setting — never attempts to stop or restart the Connector.
   * Turning the setting off takes effect on the Connector's next (re)start, matching how every
   * other RESTART_REQUIRED_FIELDS setting already behaves; a currently-active pairing session is
   * left for the user to explicitly cancel via cancelPairing() if desired.
   */
  disableSecurePairing(): SettingsMutationResult {
    return this.options.setSecureMobilePairingEnabled(false);
  }

  private capability(
    state: SecurePairingCapability['state'],
    userMessage: string | null,
  ): SecurePairingCapability {
    return { state, connectorName: null, transportFingerprint: null, trustedDeviceCount: null, userMessage };
  }

  private buildControlClient(): ConnectorHttpClient {
    const token = this.options.getControlToken();
    return new ConnectorHttpClient({
      baseUrl: this.options.getConnectorBaseUrl(),
      fetchImpl: this.options.fetchImpl,
      maxAttempts: 1,
      timeoutMs: this.options.requestTimeoutMs ?? 8_000,
      defaultHeaders: token ? { [DESKTOP_CONTROL_TOKEN_HEADER]: token } : {},
    });
  }

  private async renderQrDataUrl(created: PairingSessionCreateResult): Promise<string> {
    const payload = buildPairingQrPayload(created);
    return QRCode.toDataURL(JSON.stringify(payload), {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 256,
    });
  }

  /** Best-effort only — at most one session can be active per Connector, so the most recently
   *  created credential is a reasonable (not perfectly rigorous) match for "the device that just
   *  redeemed this session." A missed/ambiguous match still leaves the trusted-device list itself
   *  correct; only the immediate "Redeemed" panel's device-label text is affected. */
  private async tryResolveRecentlyRedeemedDeviceLabel(client: ConnectorHttpClient): Promise<string | null> {
    try {
      const result = await client.listPairingCredentials();
      const [mostRecent] = [...result.items].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
      );
      return mostRecent?.deviceLabel ?? null;
    } catch {
      return null;
    }
  }

  private toView(): ActivePairingSessionView {
    const base = {
      pairingSessionId: null,
      qrDataUrl: null,
      shortCode: null,
      expiresAt: null,
      redeemedDeviceLabel: null,
      userMessage: null,
    } as const;
    const s = this.sessionState;
    switch (s.kind) {
      case 'idle':
        return { state: 'idle', ...base };
      case 'creating':
        return { state: 'creating', ...base };
      case 'active':
        return {
          state: 'active',
          ...base,
          pairingSessionId: s.pairingSessionId,
          qrDataUrl: s.qrDataUrl,
          shortCode: s.shortCode,
          expiresAt: s.expiresAt,
        };
      case 'redeemed':
        return { state: 'redeemed', ...base, redeemedDeviceLabel: s.deviceLabel };
      case 'expired':
        return { state: 'expired', ...base, userMessage: 'This pairing session expired.' };
      case 'cancelled':
        return { state: 'cancelled', ...base };
      case 'failed':
        return { state: 'failed', ...base, userMessage: s.message };
    }
  }
}
