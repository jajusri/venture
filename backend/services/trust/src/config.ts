export interface TrustServiceConfig { readonly host: string; readonly port: number; readonly databaseUrl: string; readonly databasePoolMax: number }
export function readTrustServiceConfig(env: NodeJS.ProcessEnv = process.env): TrustServiceConfig {
  const port = Number(env.BUDCOM_TRUST_PORT ?? '8080');
  if (!Number.isInteger(port) || port < 0 || port > 65_535) throw new Error('BUDCOM_TRUST_PORT must be a valid TCP port');
  const databaseUrl = env.BUDCOM_TRUST_DATABASE_URL;
  if (!databaseUrl) throw new Error('BUDCOM_TRUST_DATABASE_URL is required');
  const databasePoolMax = Number(env.BUDCOM_TRUST_DATABASE_POOL_MAX ?? '20');
  if (!Number.isInteger(databasePoolMax) || databasePoolMax < 1 || databasePoolMax > 200) throw new Error('BUDCOM_TRUST_DATABASE_POOL_MAX must be between 1 and 200');
  return { host: env.BUDCOM_TRUST_HOST ?? '127.0.0.1', port, databaseUrl, databasePoolMax };
}
