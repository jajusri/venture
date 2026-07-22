import type { TallyConnectionManager } from '../../tally/connection/tally-connection-manager.js';
import type { TallyDiagnosticsService } from '../interfaces/tally-diagnostics.js';

export class TallyDiagnosticsServiceImpl implements TallyDiagnosticsService {
  constructor(private readonly connectionManager: TallyConnectionManager) {}

  getConnectionDiagnostics() {
    return this.connectionManager.getDiagnostics();
  }
}
