import { identifier, type DeviceId, type DeviceKeyId } from '../domain/authority.js';
import { TrustServiceError } from '../errors.js';
import type { DeviceEnrollmentInput } from '../application/consume-enrollment-grant.js';

export interface DeviceEnrollmentRequestBody {
  readonly grantId?: unknown; readonly grantSecret?: unknown; readonly deviceId?: unknown;
  readonly deviceKeyId?: unknown; readonly deviceKeyVersion?: unknown; readonly publicKey?: unknown; readonly publicKeyFingerprint?: unknown;
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new TrustServiceError('invalid_enrollment_request', `${field} is required`, 400);
  return value;
}

function decodeBase64Field(value: unknown, field: string): Uint8Array {
  const raw = requiredString(value, field);
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(raw)) throw new TrustServiceError('invalid_enrollment_request', `${field} must be base64`, 400);
  const decoded = Buffer.from(raw, 'base64');
  if (decoded.toString('base64') !== raw) throw new TrustServiceError('invalid_enrollment_request', `${field} must be base64`, 400);
  return Uint8Array.from(decoded);
}

export function mapDeviceEnrollmentRequestBody(body: DeviceEnrollmentRequestBody): DeviceEnrollmentInput {
  const deviceKeyVersion = Number(body.deviceKeyVersion);
  if (!Number.isInteger(deviceKeyVersion) || deviceKeyVersion < 1) throw new TrustServiceError('invalid_enrollment_request', 'deviceKeyVersion must be a positive integer', 400);
  return {
    grantId: requiredString(body.grantId, 'grantId'),
    grantSecret: requiredString(body.grantSecret, 'grantSecret'),
    deviceId: identifier(requiredString(body.deviceId, 'deviceId'), 'DeviceId') as DeviceId,
    deviceKeyId: identifier(requiredString(body.deviceKeyId, 'deviceKeyId'), 'DeviceKeyId') as DeviceKeyId,
    deviceKeyVersion,
    publicKey: decodeBase64Field(body.publicKey, 'publicKey'),
    publicKeyFingerprint: requiredString(body.publicKeyFingerprint, 'publicKeyFingerprint'),
  };
}
