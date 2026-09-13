import { describe, expect, it, vi } from 'vitest';

import {
  VENTURE_MDNS_SERVICE_TYPE,
  MdnsAdvertiser,
  type MdnsPublishOptions,
  type MdnsPublisher,
} from '../../../src/services/discovery/mdns-advertiser.js';
import { ConnectorIdentityRepository } from '../../../src/services/identity/connector-identity-repository.js';

const noopLogger = {
  info: () => {},
  warn: () => {},
  error: () => {},
  debug: () => {},
  child: () => noopLogger,
};

function createFakePublisher() {
  const published: MdnsPublishOptions[] = [];
  const stopped: MdnsPublishOptions[] = [];
  let destroyed = false;
  const publisher: MdnsPublisher = {
    publish: (options) => {
      published.push(options);
      return {
        stop: () => {
          stopped.push(options);
        },
      };
    },
    destroy: () => {
      destroyed = true;
    },
  };
  return { publisher, published, stopped, isDestroyed: () => destroyed };
}

function createIdentity(id = 'fixed-connector-id') {
  return new ConnectorIdentityRepository(() => {
    throw new Error('not used — VENTURE_CONNECTOR_ID supplied');
  }, { VENTURE_CONNECTOR_ID: id });
}

describe('MdnsAdvertiser', () => {
  it('advertises _venture._tcp.local with the stable connector id, port, and non-sensitive metadata only', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();

    expect(fake.published).toHaveLength(1);
    const [advertisement] = fake.published;
    expect(advertisement.type).toBe(VENTURE_MDNS_SERVICE_TYPE);
    expect(advertisement.port).toBe(8080);
    expect(advertisement.txt.connectorId).toBe('connector-abc');
    expect(advertisement.txt.apiVersion).toBe('1.0.0');
    expect(advertisement.txt.authRequired).toBe('false');
    expect(advertisement.txt.securePort).toBe('8443');
    // Only the allow-listed keys — never company/voucher/customer/token data.
    expect(Object.keys(advertisement.txt).sort()).toEqual(['apiVersion', 'authRequired', 'connectorId', 'name', 'securePort']);
  });

  // TD-017 (real root cause): the SRV port this advertisement carries has always been, and must
  // remain, the plain HTTP port (Android's legacy enrolment/reconnection paths dial it directly).
  // The authenticated-transport rediscovery path is HTTPS-only and was never told the secure
  // port at all before this fix — every rediscovery attempt tried a TLS handshake against the
  // plain HTTP port and failed unconditionally, for every candidate, on every network change.
  it('TD-017: advertises the secure transport port as a distinct TXT field, separate from the primary SRV port', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();

    const [advertisement] = fake.published;
    expect(advertisement.port).toBe(8080);
    expect(advertisement.txt.securePort).toBe('8443');
  });

  // Mirrors the disableIPv6/TD-030 invariant: never advertise a port nothing is listening on.
  it('TD-017: omits the securePort TXT field entirely when secure transport is not running', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => null,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();

    const [advertisement] = fake.published;
    expect('securePort' in advertisement.txt).toBe(false);
  });

  it('TD-017: securePort tracks a route-driven change across a republish', async () => {
    const fake = createFakePublisher();
    let securePort: number | null = 8443;
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => securePort,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();
    securePort = null;
    advertiser.republish();

    expect(fake.published[0]?.txt.securePort).toBe('8443');
    expect('securePort' in (fake.published[1]?.txt ?? {})).toBe(false);
  });

  // TD-030 regression: bonjour-service otherwise auto-advertises an AAAA record for every local
  // IPv6 address it finds, regardless of what the Connector's HTTP/HTTPS servers actually bind
  // to (always an explicit IPv4 literal, never IPv6) — a field defect where Android's mDNS
  // resolver picked one of these unreachable advertised IPv6 addresses instead of the correct,
  // reachable IPv4 one, leaving a securely paired device unable to rediscover a Connector that
  // had, in fact, correctly rebound after a real network change (see TD-029).
  it('TD-030: always disables IPv6 record publication, since the Connector never listens on IPv6', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();

    expect(fake.published[0]?.disableIPv6).toBe(true);
  });

  it('TD-030: disableIPv6 remains true across a republish (rebind never re-enables IPv6)', async () => {
    const fake = createFakePublisher();
    let port = 8080;
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => port,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();
    port = 8081;
    advertiser.republish();

    expect(fake.published[1]?.disableIPv6).toBe(true);
  });

  it('re-publishes with the route-backed port/host after a rebind, keeping the same connector id', async () => {
    const fake = createFakePublisher();
    let port = 8080;
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => port,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();
    port = 8081;
    advertiser.republish();

    expect(fake.stopped).toHaveLength(1);
    expect(fake.published).toHaveLength(2);
    expect(fake.published[1]?.port).toBe(8081);
    expect(fake.published[1]?.txt.connectorId).toBe('connector-abc');
  });

  it('stop() unpublishes and destroys the underlying publisher', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity(),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();
    expect(advertiser.isRunning()).toBe(true);

    await advertiser.stop();

    expect(advertiser.isRunning()).toBe(false);
    expect(fake.stopped).toHaveLength(1);
    expect(fake.isDestroyed()).toBe(true);
  });

  it('reflects the authRequired flag from config in the TXT record', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity(),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => true,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    await advertiser.start();

    expect(fake.published[0]?.txt.authRequired).toBe('true');
  });

  it('getCurrentAdvertisement returns null before start and the live options once running', async () => {
    const fake = createFakePublisher();
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity('connector-abc'),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher: () => fake.publisher,
    });

    expect(advertiser.getCurrentAdvertisement()).toBeNull();
    await advertiser.start();
    expect(advertiser.getCurrentAdvertisement()?.txt.connectorId).toBe('connector-abc');
  });

  it('creates the publisher only once across multiple republishes', async () => {
    const fake = createFakePublisher();
    const createPublisher = vi.fn(() => fake.publisher);
    const advertiser = new MdnsAdvertiser({
      identity: createIdentity(),
      getPort: () => 8080,
      getApiVersion: () => '1.0.0',
      getAuthRequired: () => false,
      getSecureTransportPort: () => 8443,
      logger: noopLogger,
      createPublisher,
    });

    await advertiser.start();
    advertiser.republish();
    advertiser.republish();

    expect(createPublisher).toHaveBeenCalledTimes(1);
  });
});
