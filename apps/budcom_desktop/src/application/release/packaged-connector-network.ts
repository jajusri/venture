import { isLoopbackConnectorHost } from '../connector-network-binding.js';

export const PACKAGED_CONNECTOR_LOOPBACK_HOST = '127.0.0.1';

export function resolvePackagedConnectorLoopbackHost(configuredHost: string, isPackaged: boolean): string {
  if (!isPackaged) {
    return configuredHost;
  }
  return PACKAGED_CONNECTOR_LOOPBACK_HOST;
}

export function assertPackagedConnectorHostIsLoopback(configuredHost: string, isPackaged: boolean): void {
  if (!isPackaged) {
    return;
  }
  if (!isLoopbackConnectorHost(configuredHost)) {
    throw new Error('Packaged desktop startup requires loopback connector host binding.');
  }
}
