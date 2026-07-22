import type { ServiceStatusDto, SyncDisplayStatus } from './types.js';

const SYNC_LABELS: Record<SyncDisplayStatus, string> = {
  ready: 'Ready',
  idle: 'Idle',
  syncing: 'Syncing',
  paused: 'Paused',
  failed: 'Failed',
};

export function mapSyncDisplayStatus(services: readonly ServiceStatusDto[]): SyncDisplayStatus {
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

export function getSyncLabel(status: SyncDisplayStatus): string {
  return SYNC_LABELS[status];
}

export function formatLastSync(lastValidatedAt: string | null): string {
  if (!lastValidatedAt) {
    return 'Never';
  }
  return lastValidatedAt;
}
