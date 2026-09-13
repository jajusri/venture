import type { ConnectionIndicator, ConnectionLabel, HealthResponse } from './types.js';

const CONNECTION_LABELS: Record<ConnectionIndicator, ConnectionLabel> = {
  connected: 'Connected',
  waiting: 'Waiting',
  disconnected: 'Disconnected',
  starting: 'Starting',
  error: 'Error',
  unknown: 'Unknown',
};

export function mapConnectionIndicator(
  connectorReachable: boolean,
  health: HealthResponse | null,
): ConnectionIndicator {
  if (!connectorReachable || !health) {
    return 'disconnected';
  }

  const apiServer = health.services.find((service) => service.name === 'ApiServer');
  const tallyService = health.services.find((service) => service.name === 'TallyConnection');
  const tallyState = tallyService?.message?.toLowerCase() ?? '';

  if (!health.tallyReachable) {
    return tallyState.includes('connecting') || tallyState.includes('reconnecting')
      ? 'starting'
      : 'disconnected';
  }

  if (health.status === 'unavailable') {
    return apiServer?.running === false ? 'starting' : 'error';
  }

  if (
    tallyState.includes('connecting') ||
    tallyState.includes('reconnecting') ||
    (apiServer && !apiServer.ready)
  ) {
    return 'starting';
  }

  if (health.status === 'degraded') {
    return 'waiting';
  }

  if (health.status === 'ok' && health.tallyReachable && tallyService?.ready) {
    return 'connected';
  }

  if (tallyService && !tallyService.ready) {
    return tallyService.running ? 'starting' : 'error';
  }

  return 'unknown';
}

export function getConnectionLabel(indicator: ConnectionIndicator): ConnectionLabel {
  return CONNECTION_LABELS[indicator];
}
