/**
 * Authoritative release-mode contract for Budcom desktop.
 *
 * Precedence (first match wins):
 * 1. BUDCOM_RELEASE_MODE environment variable
 * 2. build-info.json releaseMode when packaged
 * 3. development (unpackaged default)
 *
 * NODE_ENV alone must never imply production or unrestricted release.
 */

export const ReleaseMode = {
  Development: 'development',
  Test: 'test',
  ControlledPilot: 'controlled_pilot',
  Production: 'production',
} as const;

export type ReleaseMode = (typeof ReleaseMode)[keyof typeof ReleaseMode];

const KNOWN_MODES = new Set<string>(Object.values(ReleaseMode));

export interface ResolveReleaseModeInput {
  readonly envValue?: string | undefined;
  readonly buildInfoMode?: string | undefined;
  readonly isPackaged?: boolean;
}

export class UnknownReleaseModeError extends Error {
  constructor(readonly value: string) {
    super(`Unknown release mode: ${value}`);
    this.name = 'UnknownReleaseModeError';
  }
}

export function resolveReleaseMode(input: ResolveReleaseModeInput = {}): ReleaseMode {
  const raw = input.envValue ?? process.env.BUDCOM_RELEASE_MODE ?? input.buildInfoMode;
  if (raw === undefined || raw.trim().length === 0) {
    if (input.isPackaged) {
      throw new UnknownReleaseModeError('(missing — packaged build requires explicit release mode)');
    }
    return ReleaseMode.Development;
  }
  const normalized = raw.trim().toLowerCase();
  if (!KNOWN_MODES.has(normalized)) {
    throw new UnknownReleaseModeError(raw);
  }
  return normalized as ReleaseMode;
}

export function isProductionReleaseMode(mode: ReleaseMode): boolean {
  return mode === ReleaseMode.Production;
}

export function isControlledPilotMode(mode: ReleaseMode): boolean {
  return mode === ReleaseMode.ControlledPilot;
}

export function isAutoUpdatePermitted(mode: ReleaseMode): boolean {
  return mode === ReleaseMode.Production;
}

export function isDevelopmentDiagnosticsPermitted(mode: ReleaseMode): boolean {
  return mode === ReleaseMode.Development || mode === ReleaseMode.Test;
}
