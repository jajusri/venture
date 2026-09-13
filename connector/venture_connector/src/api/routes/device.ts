import type { RequestHandler } from 'express';
import { Router } from 'express';

import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import { asyncHandler } from '../../infrastructure/errors/error-handler.js';
import type { ConnectorConfig } from '../../config/defaults.js';
import type { TrustedDeviceRepository } from '../../services/device/trusted-device-repository.js';
import { createRequireDesktopControlTokenMiddleware } from '../middleware/require-desktop-control-token.js';

const MIN_INSTALLATION_ID_LENGTH = 16;
const MAX_FRIENDLY_NAME_LENGTH = 64;

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function requireRepository(repo: TrustedDeviceRepository | undefined): TrustedDeviceRepository {
  if (!repo) {
    throw new AppError(
      ErrorCodes.NOT_IMPLEMENTED,
      'Trusted-device pairing is not available in this deployment.',
      501,
    );
  }
  return repo;
}

/**
 * Pairing-bootstrap routes only — reachable with no credentials, by necessity: a device has
 * no token until it pairs, and a returning device must be able to check whether its stored
 * token still works. Mounted in `server.ts` *before* `requireTrustedDeviceAuth`.
 *
 * Neither route discloses anything the caller didn't already possess:
 *  - POST /device/pair only echoes back the companyId/companyName the caller itself supplied
 *    in the request body — it never looks up or reveals a company the caller didn't already
 *    name.
 *  - POST /device/validate-token discloses companyId/companyName only when the caller already
 *    holds the exact raw secret token (proof of a prior successful pairing); an invalid/unknown
 *    token gets `{ valid: false }` and nothing else. It is self-gating by secret possession,
 *    the same trust boundary a header-based bearer token provides, just carried in the body.
 *
 * Device-management routes that read back or mutate *other* devices' state (list, revoke,
 * look up by installationId) are deliberately NOT here — see `createDeviceManagementRouter`.
 */
export function createDevicePairingRouter(trustedDeviceRepository?: TrustedDeviceRepository): Router {
  const router = Router();

  /**
   * POST /device/pair
   *
   * COMPATIBILITY-ONLY TECHNICAL DEBT: this route issues a company-bound bearer token to any
   * caller who can reach it and already knows a companyId/companyName/installationId — with no
   * expiring session, no one-time-secret challenge, and no exact-endpoint binding in front of
   * it. It predates the pairing-session foundation added in this phase
   * (pairing-session-repository.ts / api/routes/pairing.ts) and is kept only because the
   * current Android flow has not yet been migrated to redeem through a pairing session first.
   * Secure pairing is NOT complete while this route remains reachable unchanged — its
   * restriction or removal belongs to the later integration phase, once Desktop and Android
   * both use POST /device/pairing-session/redeem. Do not present this route's continued
   * existence as evidence that pairing is secured end-to-end.
   *
   * First-time pairing: the Connector creates a cryptographically random
   * bearer token, stores only its SHA-256 hash, and returns the raw token
   * once to the caller (Android). The caller must store it securely.
   *
   * Request body:
   *   companyId        string  — currently selected company on the Connector
   *   companyName      string  — display name (informational copy for UX)
   *   installationId   string  — stable Android app-installation identifier
   *   friendlyName?    string  — user-visible device label (optional)
   *   autoConnectEnabled? boolean — opt-in auto-connect (default false)
   */
  router.post(
    '/device/pair',
    asyncHandler(async (req, res) => {
      const {
        companyId,
        companyName,
        installationId,
        friendlyName,
        autoConnectEnabled,
      } = req.body ?? {};

      if (!isNonEmptyString(companyId)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'companyId is required.', 400);
      }
      if (!isNonEmptyString(companyName)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'companyName is required.', 400);
      }
      if (!isNonEmptyString(installationId) || installationId.trim().length < MIN_INSTALLATION_ID_LENGTH) {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          `installationId must be at least ${MIN_INSTALLATION_ID_LENGTH} characters.`,
          400,
        );
      }
      if (friendlyName !== undefined && friendlyName !== null) {
        if (typeof friendlyName !== 'string' || friendlyName.length > MAX_FRIENDLY_NAME_LENGTH) {
          throw new AppError(
            ErrorCodes.VALIDATION_ERROR,
            `friendlyName must be a string of at most ${MAX_FRIENDLY_NAME_LENGTH} characters.`,
            400,
          );
        }
      }

      const result = requireRepository(trustedDeviceRepository).pair({
        companyId: companyId.trim(),
        companyName: companyName.trim(),
        installationId: installationId.trim(),
        friendlyName: typeof friendlyName === 'string' ? friendlyName.trim() || undefined : undefined,
        autoConnectEnabled: autoConnectEnabled === true,
      });

      res.status(201).json({
        deviceRecordId: result.deviceRecordId,
        // rawToken is the only time the caller receives the secret. It must be
        // stored in Android Keystore / EncryptedSharedPreferences, never logged.
        token: result.rawToken,
        companyId: result.companyId,
        companyName: result.companyName,
        autoConnectEnabled: result.autoConnectEnabled,
      });
    }),
  );

  /**
   * POST /device/validate-token
   *
   * Validates a bearer token and returns the bound company if valid.
   * This is the mechanism Android uses on subsequent launches to attempt
   * auto-connect. The Connector must still validate the session separately
   * before returning any company data.
   *
   * Self-gating: only a caller already holding the exact raw token receives
   * companyId/companyName. An invalid/unknown/revoked token returns
   * `{ valid: false }` and nothing else — this route never lets an
   * unauthenticated caller enumerate paired companies.
   */
  router.post(
    '/device/validate-token',
    asyncHandler(async (req, res) => {
      const { token } = req.body ?? {};
      if (!isNonEmptyString(token)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'token is required.', 400);
      }

      const record = requireRepository(trustedDeviceRepository).validateToken(token);
      if (!record) {
        res.status(401).json({
          valid: false,
          reason: 'Token is invalid or has been revoked.',
        });
        return;
      }

      res.json({
        valid: true,
        deviceRecordId: record.deviceRecordId,
        companyId: record.companyId,
        companyName: record.companyName,
        autoConnectEnabled: record.autoConnectEnabled,
      });
    }),
  );

  return router;
}

export interface DeviceManagementRouterDeps {
  readonly trustedDevices?: TrustedDeviceRepository;
  readonly config: Pick<ConnectorConfig, 'networkExposure' | 'desktopControlToken' | 'secureLanRouteProtectionEnabled'>;
}

/**
 * Gates GET /device/list and DELETE /device/:deviceRecordId with the existing Desktop
 * control-token boundary, but ONLY once secureLanRouteProtectionEnabled is true — while it's
 * false (the default), this is a no-op and these routes continue to rely solely on whatever the
 * shared gate mounted ahead of this router (server.ts) decides, exactly as before this phase.
 *
 * Once active, createRequireBusinessRouteAuthMiddleware deliberately exempts these two exact
 * routes from its own device-credential check (see DESKTOP_CONTROL_ONLY_ROUTES there) so this
 * middleware — not a device credential — is the sole gate: enumerating or revoking another
 * device's trust is an administrative action restricted to Desktop's own control boundary, never
 * satisfiable by an Android device principal. createRequireDesktopControlTokenMiddleware already
 * passes through unconditionally off-LAN, so loopback (Desktop's own management UI) is
 * unaffected regardless of the flag.
 */
function createConditionalDesktopControlTokenMiddleware(
  config: Pick<ConnectorConfig, 'networkExposure' | 'desktopControlToken' | 'secureLanRouteProtectionEnabled'>,
): RequestHandler {
  const requireDesktopControlToken = createRequireDesktopControlTokenMiddleware({ config });
  return (req, res, next) => {
    if (!config.secureLanRouteProtectionEnabled) {
      next();
      return;
    }
    requireDesktopControlToken(req, res, next);
  };
}

/**
 * Restricts GET /device/trusted-companies to a LegacyTrustedDevicePrincipal once
 * secureLanRouteProtectionEnabled is true and the shared gate authenticated on LAN — never a
 * bare PairingCredentialPrincipal, which carries no companyId/installationId and so cannot
 * satisfy this route's company-authorization contract without fabricating an identity mapping.
 * Reads the principal the shared gate already attached to `req` (see
 * require-business-route-auth.ts) rather than re-validating the credential a second time, so
 * this is a pure authorization-tier check, not an additional authentication step.
 *
 * A no-op whenever `req.deviceAuthPrincipal` is unset — while the flag is off, or on loopback
 * (the shared gate never authenticates loopback traffic), this route's behavior is unchanged.
 */
function requireLegacyTrustedDevicePrincipalForTrustedCompanies(
  config: Pick<ConnectorConfig, 'secureLanRouteProtectionEnabled'>,
): RequestHandler {
  return (req, res, next) => {
    if (!config.secureLanRouteProtectionEnabled) {
      next();
      return;
    }
    if (req.deviceAuthPrincipal && req.deviceAuthPrincipal.kind !== 'trusted-device') {
      res.status(403).json({
        code: 'FORBIDDEN',
        message: 'This route requires a company-bound trusted-device credential.',
      });
      return;
    }
    next();
  };
}

/**
 * Device-management routes: list, look up by installation, and revoke *other* devices'
 * trust. These read back or mutate state the caller does not necessarily already hold
 * proof of, so they must never be reachable without a valid trusted-device token when LAN
 * enforcement is enabled. Mounted in `server.ts` *after* `requireTrustedDeviceAuth`
 * (or, once secureLanRouteProtectionEnabled is true, after createRequireBusinessRouteAuthMiddleware),
 * so on loopback (Desktop's own management UI) they behave exactly as before — the same
 * middleware no-ops off-LAN — and on LAN they only open once enforcement is actually enabled.
 */
export function createDeviceManagementRouter(deps: DeviceManagementRouterDeps): Router {
  const trustedDeviceRepository = deps.trustedDevices;
  const requireDesktopControlTokenIfPolicyActive = createConditionalDesktopControlTokenMiddleware(deps.config);
  const requireLegacyPrincipalForTrustedCompanies = requireLegacyTrustedDevicePrincipalForTrustedCompanies(
    deps.config,
  );
  const router = Router();

  /**
   * GET /device/trusted-companies?installationId=…
   *
   * Returns trusted company bindings for this installation so Android can
   * suggest or automatically select a company on launch.
   *
   * The raw token is never returned; only the record metadata needed for UX.
   */
  router.get(
    '/device/trusted-companies',
    requireLegacyPrincipalForTrustedCompanies,
    asyncHandler(async (req, res) => {
      const installationId = req.query['installationId'];
      if (!isNonEmptyString(installationId) || installationId.trim().length < MIN_INSTALLATION_ID_LENGTH) {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          'installationId query parameter is required.',
          400,
        );
      }

      const records = requireRepository(trustedDeviceRepository).listByInstallation(installationId.trim());
      res.json({
        items: records.map((r) => ({
          deviceRecordId: r.deviceRecordId,
          companyId: r.companyId,
          companyName: r.companyName,
          friendlyName: r.friendlyName,
          autoConnectEnabled: r.autoConnectEnabled,
          lastUsedAt: r.lastUsedAt,
          createdAt: r.createdAt,
        })),
      });
    }),
  );

  /**
   * DELETE /device/:deviceRecordId
   *
   * Revokes a trusted-device record immediately. Subsequent token validation
   * for this record will return 401.
   */
  router.delete(
    '/device/:deviceRecordId',
    requireDesktopControlTokenIfPolicyActive,
    asyncHandler(async (req, res) => {
      const { deviceRecordId } = req.params;
      if (!isNonEmptyString(deviceRecordId)) {
        throw new AppError(ErrorCodes.VALIDATION_ERROR, 'deviceRecordId is required.', 400);
      }

      const revoked = requireRepository(trustedDeviceRepository).revoke(deviceRecordId);
      if (!revoked) {
        res.status(404).json({ ok: false, message: 'Device record not found or already revoked.' });
        return;
      }
      res.json({ ok: true, message: 'Device trust revoked.' });
    }),
  );

  /**
   * GET /device/list
   *
   * Returns all trusted-device records (for the Desktop management view).
   * Tokens are never included.
   */
  router.get(
    '/device/list',
    requireDesktopControlTokenIfPolicyActive,
    asyncHandler(async (_req, res) => {
      const records = requireRepository(trustedDeviceRepository).listAll();
      res.json({
        items: records.map((r) => ({
          deviceRecordId: r.deviceRecordId,
          companyId: r.companyId,
          companyName: r.companyName,
          friendlyName: r.friendlyName,
          installationId: r.installationId,
          autoConnectEnabled: r.autoConnectEnabled,
          createdAt: r.createdAt,
          lastUsedAt: r.lastUsedAt,
          revokedAt: r.revokedAt,
        })),
      });
    }),
  );

  return router;
}
