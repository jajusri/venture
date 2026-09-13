/**
 * Relay Service local-run configuration -- previously did not exist at all (no `config.ts`, no
 * `main.ts`; only `buildRelayService(options)` exercised in tests with fully-injected fakes, per
 * this controlled-pilot package's own discovery). Mirrors Trust's own `config.ts` shape and
 * validation style (`../../trust/src/config.ts`) rather than inventing a different convention.
 */
export interface RelayServiceConfig {
  readonly host: string; readonly port: number; readonly databaseUrl: string; readonly databasePoolMax: number;
  readonly trustBaseUrl: string; readonly trustIssuerId: string;
  readonly relayId: string; readonly relayAcceptanceKeyPath: string;
  readonly ingressMaxRequestsPerWindow: number; readonly ingressWindowMs: number;
}

export function readRelayServiceConfig(env: NodeJS.ProcessEnv = process.env): RelayServiceConfig {
  const port = Number(env.VENTURE_RELAY_PORT ?? '8082');
  if (!Number.isInteger(port) || port < 0 || port > 65_535) throw new Error('VENTURE_RELAY_PORT must be a valid TCP port');
  // Relay and Trust share one physical Postgres database by design (see migrations.ts); Relay's own
  // var is honored first so the two services CAN point at separate databases if an operator ever
  // needs that, but defaults to Trust's var so a typical local pilot only sets one connection string.
  const databaseUrl = env.VENTURE_RELAY_DATABASE_URL ?? env.VENTURE_TRUST_DATABASE_URL;
  if (!databaseUrl) throw new Error('VENTURE_RELAY_DATABASE_URL (or VENTURE_TRUST_DATABASE_URL) is required');
  const databasePoolMax = Number(env.VENTURE_RELAY_DATABASE_POOL_MAX ?? '20');
  if (!Number.isInteger(databasePoolMax) || databasePoolMax < 1 || databasePoolMax > 200) throw new Error('VENTURE_RELAY_DATABASE_POOL_MAX must be between 1 and 200');
  const trustBaseUrl = env.VENTURE_TRUST_BASE_URL ?? 'http://127.0.0.1:8080';
  const trustIssuerId = env.VENTURE_TRUST_ISSUER_ID ?? 'venture-trust-local-pilot';
  const relayId = env.VENTURE_RELAY_ID ?? 'venture-relay-local-pilot';
  const relayAcceptanceKeyPath = env.VENTURE_RELAY_ACCEPTANCE_KEY_PATH ?? '.local/relay-acceptance-key.pem';
  const ingressMaxRequestsPerWindow = Number(env.VENTURE_RELAY_INGRESS_MAX_PER_WINDOW ?? '100');
  if (!Number.isInteger(ingressMaxRequestsPerWindow) || ingressMaxRequestsPerWindow < 1) throw new Error('VENTURE_RELAY_INGRESS_MAX_PER_WINDOW must be a positive integer');
  const ingressWindowMs = Number(env.VENTURE_RELAY_INGRESS_WINDOW_MS ?? '60000');
  if (!Number.isInteger(ingressWindowMs) || ingressWindowMs < 1) throw new Error('VENTURE_RELAY_INGRESS_WINDOW_MS must be a positive integer');
  return { host: env.VENTURE_RELAY_HOST ?? '127.0.0.1', port, databaseUrl, databasePoolMax, trustBaseUrl, trustIssuerId, relayId, relayAcceptanceKeyPath, ingressMaxRequestsPerWindow, ingressWindowMs };
}
