export interface TrustServiceConfig { readonly host: string; readonly port: number }
export function readTrustServiceConfig(env: NodeJS.ProcessEnv = process.env): TrustServiceConfig {
  const port = Number(env.BUDCOM_TRUST_PORT ?? '8080');
  if (!Number.isInteger(port) || port < 0 || port > 65_535) throw new Error('BUDCOM_TRUST_PORT must be a valid TCP port');
  return { host: env.BUDCOM_TRUST_HOST ?? '127.0.0.1', port };
}
