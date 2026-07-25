/**
 * Connector-side release mode mirror of desktop contract.
 * Keep values aligned with apps/budcom_desktop/src/application/release/release-mode.ts
 */

export const ReleaseMode = {
  Development: 'development',
  Test: 'test',
  ControlledPilot: 'controlled_pilot',
  Production: 'production',
} as const;

export type ReleaseMode = (typeof ReleaseMode)[keyof typeof ReleaseMode];

const KNOWN_MODES = new Set<string>(Object.values(ReleaseMode));

export function resolveConnectorReleaseMode(envValue = process.env.BUDCOM_RELEASE_MODE): ReleaseMode {
  if (!envValue || envValue.trim().length === 0) {
    return ReleaseMode.Development;
  }
  const normalized = envValue.trim().toLowerCase();
  if (!KNOWN_MODES.has(normalized)) {
    throw new Error(`Unknown release mode: ${envValue}`);
  }
  return normalized as ReleaseMode;
}

export function isAutoUpdatePermitted(mode: ReleaseMode): boolean {
  return mode === ReleaseMode.Production;
}
