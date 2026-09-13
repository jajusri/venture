export type ConnectorNetworkExposure = 'loopback' | 'lan';

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1']);

/** Desktop-to-connector URL host — not the Tally service host. */
export function isLoopbackConnectorHost(host: string): boolean {
  return LOOPBACK_HOSTS.has(host.trim().toLowerCase());
}

export function getConnectorNetworkExposure(host: string): ConnectorNetworkExposure {
  return isLoopbackConnectorHost(host) ? 'loopback' : 'lan';
}

export function getConnectorNetworkExposureWarning(host: string): string | null {
  if (isLoopbackConnectorHost(host)) {
    return null;
  }
  return (
    `Desktop is configured to reach the connector at ${host}, which is network-exposed. ` +
    'The connector has no authentication. Use 127.0.0.1 for local-only access unless LAN mode is intentional and firewalled.'
  );
}

export function isConnectorLanModePolicySatisfied(
  host: string,
  lanModeAcknowledged: boolean,
): boolean {
  return isLoopbackConnectorHost(host) || lanModeAcknowledged;
}
