export interface TrustServiceConfig {
  readonly host: string; readonly port: number; readonly databaseUrl: string; readonly databasePoolMax: number;
  /** Controlled-pilot local signing identity -- see `persistence/local-signer.ts`'s own doc comment
   * for exactly what this is (and is not) suitable for. */
  readonly issuerId: string; readonly issuerKeyId: string; readonly issuerKeyPath: string;
}
export function readTrustServiceConfig(env: NodeJS.ProcessEnv = process.env): TrustServiceConfig {
  const port = Number(env.BUDCOM_TRUST_PORT ?? '8080');
  if (!Number.isInteger(port) || port < 0 || port > 65_535) throw new Error('BUDCOM_TRUST_PORT must be a valid TCP port');
  const databaseUrl = env.BUDCOM_TRUST_DATABASE_URL;
  if (!databaseUrl) throw new Error('BUDCOM_TRUST_DATABASE_URL is required');
  const databasePoolMax = Number(env.BUDCOM_TRUST_DATABASE_POOL_MAX ?? '20');
  if (!Number.isInteger(databasePoolMax) || databasePoolMax < 1 || databasePoolMax > 200) throw new Error('BUDCOM_TRUST_DATABASE_POOL_MAX must be between 1 and 200');
  const issuerId = env.BUDCOM_TRUST_ISSUER_ID ?? 'budcom-trust-local-pilot';
  const issuerKeyId = env.BUDCOM_TRUST_ISSUER_KEY_ID ?? 'local-pilot-key-1';
  const issuerKeyPath = env.BUDCOM_TRUST_ISSUER_KEY_PATH ?? '.local/trust-issuer-key.pem';
  return { host: env.BUDCOM_TRUST_HOST ?? '127.0.0.1', port, databaseUrl, databasePoolMax, issuerId, issuerKeyId, issuerKeyPath };
}
