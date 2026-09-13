import type { TallyDiagnosticsSnapshot } from '../../tally/core/types.js';

export interface TallyDiagnosticsService {
  getConnectionDiagnostics(): TallyDiagnosticsSnapshot;
}
