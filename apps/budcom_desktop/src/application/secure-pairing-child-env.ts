/**
 * The Connector-side default (connector/budcom_connector/src/config/defaults.ts,
 * secureTransportPort: 8443) — made explicit here rather than left implicit, per the child-env
 * wiring contract, but not independently user-configurable in this phase.
 */
export const DEFAULT_SECURE_TRANSPORT_PORT = 8443;

export interface SecurePairingChildEnvOptions {
  readonly enabled: boolean;
  readonly controlToken: string;
  readonly securePort?: number;
}

/**
 * Pure function: the exact set of Connector child-process environment variables Desktop adds
 * when secureMobilePairingEnabled is on. Returns an empty object when disabled, so merging this
 * into an existing childEnv via buildConnectorChildEnvironment() is always a safe no-op while
 * the feature is off — existing HTTP-only Connector launches are byte-for-byte unaffected.
 *
 * Deliberately narrow: only the four keys the Connector-side pairing/transport foundation reads
 * (BUDCOM_SECURE_PAIRING_ENABLED, BUDCOM_SECURE_TRANSPORT_ENABLED, BUDCOM_SECURE_TRANSPORT_PORT,
 * BUDCOM_DESKTOP_CONTROL_TOKEN — see connector/budcom_connector/src/config/index.ts). Each must
 * also be present in CONNECTOR_CHILD_ENV_ALLOWLIST (release/connector-packaged-paths.ts) or
 * buildConnectorChildEnvironment silently strips it.
 */
export function buildSecurePairingChildEnvOverrides(
  options: SecurePairingChildEnvOptions,
): Record<string, string> {
  if (!options.enabled) {
    return {};
  }
  return {
    BUDCOM_SECURE_PAIRING_ENABLED: 'true',
    BUDCOM_SECURE_TRANSPORT_ENABLED: 'true',
    BUDCOM_SECURE_TRANSPORT_PORT: String(options.securePort ?? DEFAULT_SECURE_TRANSPORT_PORT),
    BUDCOM_DESKTOP_CONTROL_TOKEN: options.controlToken,
  };
}
