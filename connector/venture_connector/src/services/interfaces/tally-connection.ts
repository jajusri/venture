import type { ServiceLifecycle, ServiceStatus } from '../../core/types.js';
import type { TallyDiagnosticsSnapshot } from '../../tally/core/types.js';

/**
 * Lifecycle and diagnostics surface for the Tally connection.
 *
 * SECURITY: there is deliberately no {@code exchange()} or raw-XML method. The
 * only supported production communication path for business reads is the
 * ERP-neutral {@link ErpReadPort} (backed by the internal read gateway).
 */
export interface TallyConnectionService extends ServiceLifecycle {
  ping(): Promise<boolean>;
  getDiagnostics(): TallyDiagnosticsSnapshot;
  getStatus(): ServiceStatus;
}
