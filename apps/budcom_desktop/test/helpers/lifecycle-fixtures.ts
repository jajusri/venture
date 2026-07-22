export const lifecycleStatusFixture = {
  state: 'connected' as const,
  stateLabel: 'Connected' as const,
  managedByDesktop: false,
  externalProcessDetected: true,
  lastSuccessfulHealthCheck: '2026-07-23T00:00:00.000Z',
  lastError: null,
  restartAttempts: 0,
  processExitCode: null,
  connectorExecutable: process.execPath,
  connectorPort: 8080,
  userMessage: null,
};

export const settingsFixture = {
  connectorUrl: 'http://localhost:8080',
  apiVersion: '1.0.0',
  desktopVersion: '0.4.2',
  erpType: 'tally',
  pollIntervalSeconds: 5,
  connectorExecutable: process.execPath,
  connectorPort: 8080,
  autoStartConnector: true,
};
