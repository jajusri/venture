export type LifecycleFailureReason =
  | 'EXECUTABLE_MISSING'
  | 'PORT_IN_USE'
  | 'STARTUP_TIMEOUT'
  | 'PERMISSION_DENIED'
  | 'PROCESS_CRASH'
  | 'INVALID_EXECUTABLE'
  | 'ALREADY_RUNNING'
  | 'MAX_RESTARTS'
  | 'STOP_FAILED';

const MESSAGES: Record<LifecycleFailureReason, string> = {
  EXECUTABLE_MISSING: 'Connector executable was not found. Build the connector or update the executable path in settings.',
  PORT_IN_USE: 'Connector port is already in use by another process. Stop the other process or change the connector port.',
  STARTUP_TIMEOUT: 'Connector did not become ready in time. Check logs and try again.',
  PERMISSION_DENIED: 'Permission denied while starting the connector. Run the desktop app with sufficient privileges.',
  PROCESS_CRASH: 'Connector process stopped unexpectedly. A restart will be attempted automatically.',
  INVALID_EXECUTABLE: 'Connector executable path is invalid. Update the configured path.',
  ALREADY_RUNNING: 'Connector is already running. Connected to the existing process.',
  MAX_RESTARTS: 'Connector failed to restart after multiple attempts. Manual intervention is required.',
  STOP_FAILED: 'Unable to stop the connector process gracefully.',
};

export function mapLifecycleUserMessage(reason: LifecycleFailureReason, detail?: string): string {
  if (detail) {
    return detail;
  }
  return MESSAGES[reason];
}

export function mapSpawnError(error: unknown): { reason: LifecycleFailureReason; message: string } {
  if (error instanceof Error) {
    const normalized = error.message.toLowerCase();
    if (normalized.includes('eacces') || normalized.includes('permission denied')) {
      return { reason: 'PERMISSION_DENIED', message: MESSAGES.PERMISSION_DENIED };
    }
    if (normalized.includes('enoent')) {
      return { reason: 'EXECUTABLE_MISSING', message: MESSAGES.EXECUTABLE_MISSING };
    }
    if (normalized.includes('eaddrinuse')) {
      return { reason: 'PORT_IN_USE', message: MESSAGES.PORT_IN_USE };
    }
  }
  return { reason: 'INVALID_EXECUTABLE', message: MESSAGES.INVALID_EXECUTABLE };
}

export function mapLifecycleStateLabel(
  state: 'starting' | 'connected' | 'reconnecting' | 'disconnected' | 'failed',
): 'Starting' | 'Connected' | 'Reconnecting' | 'Disconnected' | 'Failed' {
  switch (state) {
    case 'starting':
      return 'Starting';
    case 'connected':
      return 'Connected';
    case 'reconnecting':
      return 'Reconnecting';
    case 'failed':
      return 'Failed';
    default:
      return 'Disconnected';
  }
}
