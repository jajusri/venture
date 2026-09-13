import { createLogger } from '../../src/infrastructure/logging/logger.js';
import {
  VoucherSyncFailureAuditor,
  type VoucherSyncFailureAuditorOptions,
} from '../../src/services/voucher/voucher-sync-failure-audit.js';

const DEFAULT_TEST_MAX_BYTES = 256;
const DEFAULT_TEST_MAX_FILES = 3;

export function createTestVoucherSyncFailureAuditor(
  auditPath: string,
  overrides: Partial<VoucherSyncFailureAuditorOptions> = {},
): VoucherSyncFailureAuditor {
  return new VoucherSyncFailureAuditor({
    auditPath,
    enabled: true,
    maxBytes: DEFAULT_TEST_MAX_BYTES,
    maxFiles: DEFAULT_TEST_MAX_FILES,
    logger: createLogger({ service: 'test', level: 'error' }),
    ...overrides,
  });
}

export { DEFAULT_TEST_MAX_BYTES, DEFAULT_TEST_MAX_FILES };
