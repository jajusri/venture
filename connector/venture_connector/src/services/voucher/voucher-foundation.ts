import { VOUCHER_CONTRACT_VERSION } from '../../erp/voucher/voucher-domain.js';
import { VOUCHER_VALIDATION_LIMITS } from '../../erp/voucher/voucher-validation.js';

export interface VoucherFoundation {
  readonly contractVersion: typeof VOUCHER_CONTRACT_VERSION;
  readonly sourceNeutral: true;
  readonly readOnly: true;
  readonly internallyComposed: true;
  readonly customerOperational: false;
  readonly persistenceEnabled: true;
  readonly synchronizationInternallyComposed: true;
  readonly validationLimits: typeof VOUCHER_VALIDATION_LIMITS;
}

/**
 * Registration metadata only. Internal composition does not imply API
 * exposure, automatic execution, or customer rollout.
 */
export const voucherFoundation: VoucherFoundation = Object.freeze({
  contractVersion: VOUCHER_CONTRACT_VERSION,
  sourceNeutral: true,
  readOnly: true,
  internallyComposed: true,
  customerOperational: false,
  persistenceEnabled: true,
  synchronizationInternallyComposed: true,
  validationLimits: VOUCHER_VALIDATION_LIMITS,
});
