import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import {
  ALLOWED_REQUEST_KINDS,
  FORBIDDEN_REQUEST_TOKENS,
  ONLY_ALLOWED_TALLY_REQUEST,
} from '../security/capabilities.js';
import {
  assertXmlContainsNoProhibitedMutationConstructs,
  ProhibitedMutationConstructError,
} from './prohibited-mutation-xml.js';

function containsInvalidControlCharacters(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x1f && code !== 0x09 && code !== 0x0a && code !== 0x0d) {
      return true;
    }
  }
  return false;
}
const ENVELOPE_PATTERN = /^<ENVELOPE>[\s\S]*<\/ENVELOPE>$/i;

/**
 * SECURITY: the egress validator is structurally read-only. Only EXPORT is
 * permitted as TALLYREQUEST and only DATA/COLLECTION/OBJECT as TYPE. Write and
 * server-execution verbs (IMPORT/EXECUTE/FUNCTION/CREATE/ALTER/DELETE/UPDATE...)
 * are rejected before any transport occurs. This check is mandatory and is not
 * affected by SAFE_MODE or any configuration flag.
 */
function assertContainsNoForbiddenToken(label: string, value: string | undefined): void {
  if (!value) return;
  const upper = value.toUpperCase();
  for (const token of FORBIDDEN_REQUEST_TOKENS) {
    if (upper.includes(token)) {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        `Forbidden write/execute verb "${token}" detected in ${label}`,
        400,
        { label, value },
      );
    }
  }
}

export interface XmlValidationResult {
  readonly collectionId?: string;
  readonly requestType?: string;
  readonly requestKind?: string;
}

export function validateTallyRequestXml(xml: string, maxBytes: number): XmlValidationResult {
  const byteLength = Buffer.byteLength(xml, 'utf8');
  if (byteLength === 0) {
    throw new AppError(ErrorCodes.VALIDATION_ERROR, 'Tally XML request is empty', 400);
  }
  if (byteLength > maxBytes) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      `Tally XML request exceeds maximum size (${maxBytes} bytes)`,
      400,
      { byteLength, maxBytes },
    );
  }
  if (containsInvalidControlCharacters(xml)) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      'Tally XML request contains invalid control characters',
      400,
    );
  }
  const trimmed = xml.trim();
  if (!ENVELOPE_PATTERN.test(trimmed)) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      'Tally XML request must be a single ENVELOPE document',
      400,
    );
  }

  try {
    assertXmlContainsNoProhibitedMutationConstructs(trimmed, { requireExportRequest: true });
  } catch (error) {
    if (error instanceof ProhibitedMutationConstructError) {
      throw new AppError(ErrorCodes.VALIDATION_ERROR, error.message, 400);
    }
    throw error;
  }

  const tallyRequest = extractTagValue(trimmed, 'TALLYREQUEST')?.toUpperCase();
  const type = extractTagValue(trimmed, 'TYPE')?.toUpperCase();
  const id = extractTagValue(trimmed, 'ID');

  assertContainsNoForbiddenToken('TALLYREQUEST', tallyRequest);
  assertContainsNoForbiddenToken('TYPE', type);

  if (!tallyRequest) {
    throw new AppError(ErrorCodes.VALIDATION_ERROR, 'Tally XML request missing TALLYREQUEST', 400);
  }
  if (tallyRequest !== ONLY_ALLOWED_TALLY_REQUEST) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      `Only ${ONLY_ALLOWED_TALLY_REQUEST} requests are permitted; got TALLYREQUEST=${tallyRequest}`,
      400,
    );
  }
  if (!type || !ALLOWED_REQUEST_KINDS.has(type)) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      `Unsupported TYPE value: ${type ?? '(missing)'}`,
      400,
    );
  }
  if (!id?.trim()) {
    throw new AppError(ErrorCodes.VALIDATION_ERROR, 'Tally XML request missing ID', 400);
  }

  return {
    collectionId: id,
    requestType: tallyRequest,
    requestKind: type,
  };
}

function extractTagValue(xml: string, tag: string): string | undefined {
  const pattern = new RegExp(`<${tag}>([\\s\\S]*?)<\\/${tag}>`, 'i');
  const match = xml.match(pattern);
  return match?.[1]?.trim();
}

export function redactTallyRequestXml(xml: string): string {
  return xml
    .replace(/<SVCURRENTCOMPANY>[^<]*<\/SVCURRENTCOMPANY>/gi, '<SVCURRENTCOMPANY>[REDACTED]</SVCURRENTCOMPANY>')
    .replace(/<NAME>[^<]*<\/NAME>/gi, '<NAME>[REDACTED]</NAME>')
    .replace(/<GSTREGISTRATIONNUMBER>[^<]*<\/GSTREGISTRATIONNUMBER>/gi, '<GSTREGISTRATIONNUMBER>[REDACTED]</GSTREGISTRATIONNUMBER>');
}
