import type { Logger } from '../../infrastructure/logging/logger.js';
import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { ConnectorIdentityRepository } from '../identity/connector-identity-repository.js';

/** _budcom._tcp.local — bonjour-service appends the `_tcp` suffix itself from `type`. */
export const BUDCOM_MDNS_SERVICE_TYPE = 'budcom';

export interface MdnsPublishedHandle {
  stop(callback?: () => void): void;
}

export interface MdnsPublishOptions {
  readonly name: string;
  readonly type: string;
  readonly port: number;
  readonly txt: Record<string, string>;
  /**
   * TD-030: the underlying publisher (bonjour-service) otherwise auto-advertises an A/AAAA
   * record for every non-internal local address it finds via os.networkInterfaces(), regardless
   * of what the Connector's HTTP/HTTPS servers actually bind to. The Connector always binds an
   * explicit IPv4 literal (see route-backed-lifecycle-override.ts/active-network-resolver.ts) and
   * never listens on IPv6 — so IPv6 records are never anything but a false, unreachable
   * advertisement. Always true; kept as an explicit field (not a hardcoded publisher default) so
   * the invariant is visible at the call site and covered by a test, rather than living silently
   * inside the publisher implementation.
   */
  readonly disableIPv6: true;
}

/** Narrow seam over bonjour-service so tests never open a real multicast socket. */
export interface MdnsPublisher {
  publish(options: MdnsPublishOptions): MdnsPublishedHandle;
  destroy(): void;
}

export type MdnsPublisherFactory = () => MdnsPublisher;

export interface MdnsAdvertiserDeps {
  readonly identity: ConnectorIdentityRepository;
  readonly getPort: () => number;
  readonly getApiVersion: () => string;
  readonly getAuthRequired: () => boolean;
  /**
   * TD-017 (real root cause): the SRV `port` this advertisement carries is, and must remain, the
   * plain HTTP API port — Android's legacy/unauthenticated connector-enrolment and reconnection
   * paths (`ConnectorConnectionResolver`, `ConnectorEnrolmentService`) dial it directly as
   * `http://host:port`. The authenticated transport's rediscovery (`AuthenticatedConnectorEndpointResolver`)
   * is HTTPS-only and needs the *separate* secure port — which was never advertised at all before
   * this fix, so every rediscovery attempt tried a TLS handshake against the plain HTTP port and
   * failed every single time. Returns null (and the TXT field is omitted entirely) when secure
   * transport isn't actually running, so a candidate is never advertised as authenticated-capable
   * when nothing is listening there — same "never advertise what you don't listen on" invariant
   * as `disableIPv6` above.
   */
  readonly getSecureTransportPort: () => number | null;
  readonly logger: Logger;
  readonly createPublisher: MdnsPublisherFactory;
}

/**
 * Advertises the Connector over standards-based mDNS/DNS-SD (_budcom._tcp.local) so Android can
 * discover it without a hardcoded IP. The TXT record intentionally carries only non-sensitive
 * discovery metadata (connector ID, friendly name, port, API version, auth-required flag) — never
 * company names, vouchers, customer data, tokens, or database details.
 */
export class MdnsAdvertiser implements ServiceLifecycle {
  private publisher: MdnsPublisher | null = null;
  private handle: MdnsPublishedHandle | null = null;
  private running = false;

  constructor(private readonly deps: MdnsAdvertiserDeps) {}

  async start(): Promise<void> {
    this.publishCurrent();
  }

  async stop(): Promise<void> {
    this.unpublish();
    this.publisher?.destroy();
    this.publisher = null;
    this.running = false;
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'MdnsAdvertiser',
      running: this.running,
      ready: this.running,
      message: this.running ? 'Advertising _budcom._tcp.local' : 'Not advertising',
    };
  }

  /** Re-publishes with the current port/TXT after a route-driven host rebind. */
  republish(): void {
    this.unpublish();
    this.publishCurrent();
  }

  getCurrentAdvertisement(): MdnsPublishOptions | null {
    if (!this.running) {
      return null;
    }
    return this.buildOptions();
  }

  private publishCurrent(): void {
    if (!this.publisher) {
      this.publisher = this.deps.createPublisher();
    }
    const options = this.buildOptions();
    this.handle = this.publisher.publish(options);
    this.running = true;
    this.deps.logger.info('mdns_advertising_started', {
      component: 'mdns-advertiser',
      connectorId: options.txt.connectorId,
      port: options.port,
    });
  }

  private buildOptions(): MdnsPublishOptions {
    const identity = this.deps.identity.getOrCreateIdentity();
    const securePort = this.deps.getSecureTransportPort();
    const txt: Record<string, string> = {
      connectorId: identity.connectorId,
      name: identity.connectorName,
      apiVersion: this.deps.getApiVersion(),
      authRequired: String(this.deps.getAuthRequired()),
    };
    if (securePort !== null) {
      txt.securePort = String(securePort);
    }
    return {
      name: identity.connectorName,
      type: BUDCOM_MDNS_SERVICE_TYPE,
      port: this.deps.getPort(),
      txt,
      disableIPv6: true,
    };
  }

  private unpublish(): void {
    this.handle?.stop();
    this.handle = null;
  }
}
