import type { ConnectionIndicator, HealthResponse } from './types.js';

const CONNECTION_LABELS: Record<ConnectionIndicator, string> = {
  connected: 'Connected',
  waiting: 'Waiting',
  disconnected: 'Disconnected',
  unknown: 'Unknown',
};

export function mapConnectionIndicator(
  connectorReachable: boolean,
  health: HealthResponse | null,
): ConnectionIndicator {
  if (!connectorReachable || !health) {
    return 'unknown';
  }

  const tallyService = health.services.find((service) => service.name === 'TallyConnection');
  const tallyState = tallyService?.message?.toLowerCase() ?? '';

  if (health.status === 'unavailable' || !health.tallyReachable) {
    return 'disconnected';
  }

  if (
    health.status === 'degraded' ||
    tallyState.includes('degraded') ||
    tallyState.includes('connecting') ||
    tallyState.includes('reconnecting')
  ) {
    return 'waiting';
  }

  if (health.status === 'ok' && health.tallyReachable && tallyService?.ready) {
    return 'connected';
  }

  if (!tallyService?.ready) {
    return 'waiting';
  }

  return 'unknown';
}

export function getConnectionLabel(indicator: ConnectionIndicator): string {
  return CONNECTION_LABELS[indicator];
}
