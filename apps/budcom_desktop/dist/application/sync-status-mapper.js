"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.mapSyncDisplayStatus = mapSyncDisplayStatus;
exports.getSyncLabel = getSyncLabel;
exports.formatLastSync = formatLastSync;
const SYNC_LABELS = {
    ready: 'Ready',
    idle: 'Idle',
    syncing: 'Syncing',
    paused: 'Paused',
    failed: 'Failed',
};
function mapSyncDisplayStatus(services) {
    const sync = services.find((service) => service.name === 'SyncEngine');
    if (!sync) {
        return 'idle';
    }
    if (!sync.running) {
        return 'paused';
    }
    if (!sync.ready) {
        return 'syncing';
    }
    if (sync.message?.toLowerCase().includes('placeholder')) {
        return 'idle';
    }
    return 'ready';
}
function getSyncLabel(status) {
    return SYNC_LABELS[status];
}
function formatLastSync(lastValidatedAt) {
    if (!lastValidatedAt) {
        return 'Never';
    }
    return lastValidatedAt;
}
//# sourceMappingURL=sync-status-mapper.js.map