import type { ServiceStatusDto, SyncDisplayStatus } from './types.js';

const SYNC_LABELS: Record<SyncDisplayStatus, string> = {
  ready: 'Ready',
  idle: 'Idle',
  syncing: 'Syncing',
  paused: 'Paused',
  failed: 'Failed',
};

export function mapSyncDisplayStatus(services: readonly ServiceStatusDto[]): SyncDisplayStatus {
  const ledgerSync = services.find((service) => service.name === 'LedgerSync');
  const sync = ledgerSync ?? services.find((service) => service.name === 'SyncEngine');
  if (!sync) {
    return 'idle';
  }
  if (!sync.running) {
    return 'paused';
  }
  const message = sync.message?.toLowerCase() ?? '';
  if (message === 'running') {
    return 'syncing';
  }
  if (message === 'failed') {
    return 'failed';
  }
  if (message === 'completed' || message === 'idle') {
    return 'ready';
  }
  if (sync.message?.toLowerCase().includes('placeholder')) {
    return 'idle';
  }
  if (!sync.ready) {
    return 'syncing';
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
