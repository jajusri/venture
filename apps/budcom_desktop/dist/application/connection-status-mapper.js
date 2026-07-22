"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.mapConnectionIndicator = mapConnectionIndicator;
exports.getConnectionLabel = getConnectionLabel;
const CONNECTION_LABELS = {
    connected: 'Connected',
    waiting: 'Waiting',
    disconnected: 'Disconnected',
    unknown: 'Unknown',
};
function mapConnectionIndicator(connectorReachable, health) {
    if (!connectorReachable || !health) {
        return 'unknown';
    }
    const tallyService = health.services.find((service) => service.name === 'TallyConnection');
    const tallyState = tallyService?.message?.toLowerCase() ?? '';
    if (health.status === 'unavailable' || !health.tallyReachable) {
        return 'disconnected';
    }
    if (health.status === 'degraded' ||
        tallyState.includes('degraded') ||
        tallyState.includes('connecting') ||
        tallyState.includes('reconnecting')) {
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
function getConnectionLabel(indicator) {
    return CONNECTION_LABELS[indicator];
}
//# sourceMappingURL=connection-status-mapper.js.map