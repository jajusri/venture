export const CONNECTOR_VERSION = '0.1.0';
export const SCHEMA_VERSION = '1.0.0';

export interface ConnectorConfig {
  readonly env: 'development' | 'production' | 'test';
  readonly host: string;
  readonly port: number;
  readonly logLevel: 'debug' | 'info' | 'warn' | 'error';
  readonly tallyHost: string;
  readonly tallyPort: number;
  readonly databasePath: string;
  readonly gracefulShutdownMs: number;
  readonly connectorVersion: string;
  readonly schemaVersion: string;
}

export const defaultConfig: ConnectorConfig = {
  env: 'development',
  host: '0.0.0.0',
  port: 8080,
  logLevel: 'info',
  tallyHost: 'localhost',
  tallyPort: 9000,
  databasePath: './data/budcom-connector.db',
  gracefulShutdownMs: 10_000,
  connectorVersion: CONNECTOR_VERSION,
  schemaVersion: SCHEMA_VERSION,
};
