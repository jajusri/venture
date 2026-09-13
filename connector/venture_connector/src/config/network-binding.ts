export type ConnectorNetworkExposure = 'loopback' | 'lan';

export interface ParsedConnectorBindHost {
  readonly host: string;
  readonly exposure: ConnectorNetworkExposure;
  readonly isLoopback: boolean;
}

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1']);

/** Connector API bind host — not the Tally service host. */
export function isLoopbackConnectorHost(host: string): boolean {
  return LOOPBACK_HOSTS.has(host.trim().toLowerCase());
}

export function parseConnectorBindHost(raw: string | undefined, fallback: string): ParsedConnectorBindHost {
  const host = (raw ?? fallback).trim();
  if (!host) {
    throw new Error('Connector bind host must not be empty.');
  }
  if (!/^[a-zA-Z0-9.:-]+$/.test(host)) {
    throw new Error(`Invalid connector bind host: ${host}`);
  }
  if (host === '0.0.0.0' || host === '::') {
    throw new Error(
      'Connector bind host 0.0.0.0 is not permitted. Use 127.0.0.1 for local-only access or configure a specific LAN address explicitly.',
    );
  }
  const isLoopback = isLoopbackConnectorHost(host);
  return {
    host,
    exposure: isLoopback ? 'loopback' : 'lan',
    isLoopback,
  };
}

export function getNetworkExposureWarning(
  parsed: ParsedConnectorBindHost,
  authenticatedLanRoutes = false,
): string | null {
  if (parsed.isLoopback) {
    return null;
  }
  const protection = authenticatedLanRoutes
    ? 'LAN business routes require device authentication.'
    : 'Authentication is not enabled.';
  return `Connector API is network-exposed on ${parsed.host}. ${protection} Restrict network access with firewall rules or bind to 127.0.0.1 for local-only operation.`;
}

export function isLanModePolicySatisfied(
  parsed: ParsedConnectorBindHost,
  lanModeAcknowledged: boolean,
): boolean {
  return parsed.isLoopback || lanModeAcknowledged;
}
