import type { ActiveNetworkAdapter } from './active-network-resolver.js';
import { buildConnectorBaseUrl } from '../desktop-config-schema.js';
import type { ConnectorLifecycleConfig } from '../connector-lifecycle-types.js';

/**
 * In trusted-LAN mode, replaces the configured/typed connectorHost with the live route-resolved
 * adapter's address, so the Connector is always spawned bound to the address Windows itself
 * reports as the current default-route adapter — never a stale, hand-typed IP. Local-only mode
 * and manual Advanced-fallback configuration (no resolved adapter yet) are left untouched.
 */
export function applyRouteBackedHost(
  lifecycleConfig: ConnectorLifecycleConfig,
  activeNetwork: ActiveNetworkAdapter | null,
): ConnectorLifecycleConfig {
  if (lifecycleConfig.connectorBindMode !== 'trusted-lan' || !activeNetwork) {
    return lifecycleConfig;
  }
  if (activeNetwork.ipv4 === lifecycleConfig.connectorHost) {
    return lifecycleConfig;
  }

  const connectorBaseUrl = buildConnectorBaseUrl(activeNetwork.ipv4, lifecycleConfig.connectorPort);
  return {
    ...lifecycleConfig,
    connectorHost: activeNetwork.ipv4,
    connectorBaseUrl,
    childEnv: {
      ...lifecycleConfig.childEnv,
      VENTURE_CONNECTOR_HOST: activeNetwork.ipv4,
    },
  };
}
