import { afterEach, describe, expect, it } from 'vitest';

import { StockItemSyncServiceImpl } from '../../../src/services/stock-item/stock-item-sync.service.js';
import { createLogger } from '../../../src/infrastructure/logging/logger.js';
import { createPermissiveSessionMock } from '../../helpers/session-mock.js';
import {
  cleanupTestSqliteStorage,
  createTestConnectorConfig,
  createTestSqliteStorage,
} from '../../helpers/sqlite-test-storage.js';

afterEach(async () => {
  await cleanupTestSqliteStorage();
});

describe('StockItemSyncService run isolation', () => {
  it('does not return ledger sync runs from stock item run lookup', async () => {
    const { storage, basePath } = await createTestSqliteStorage();
    const bundle = storage.getBundle();
    const ledgerRun = bundle.syncRunRepository.createRun({
      companyId: 'estimation',
      resourceKind: 'ledgers',
      syncType: 'full',
      connectorVersion: '0.3.1',
      schemaVersion: '3',
    });
    bundle.syncRunRepository.updateRun({
      ...ledgerRun,
      status: 'completed',
      completedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    const service = new StockItemSyncServiceImpl(
      createTestConnectorConfig(basePath),
      {} as never,
      { resolveName: async () => 'Demo' } as never,
      createPermissiveSessionMock(),
      createLogger({ service: 'test', level: 'error' }),
      storage,
    );
    await service.start();

    const run = await service.getSyncRun(ledgerRun.syncRunId);
    expect(run).toBeNull();
  });
});
