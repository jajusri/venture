import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';

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
const ALLOWED_TYPES = new Set(['EXPORT', 'IMPORT', 'EXECUTE']);
const ALLOWED_REQUEST_KINDS = new Set(['DATA', 'COLLECTION', 'OBJECT', 'FUNCTION']);

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

  const tallyRequest = extractTagValue(trimmed, 'TALLYREQUEST')?.toUpperCase();
  const type = extractTagValue(trimmed, 'TYPE')?.toUpperCase();
  const id = extractTagValue(trimmed, 'ID');

  if (tallyRequest && !ALLOWED_TYPES.has(tallyRequest)) {
    throw new AppError(
      ErrorCodes.VALIDATION_ERROR,
      `Unsupported TALLYREQUEST value: ${tallyRequest}`,
      400,
    );
  }
  if (type && !ALLOWED_REQUEST_KINDS.has(type)) {
    throw new AppError(ErrorCodes.VALIDATION_ERROR, `Unsupported TYPE value: ${type}`, 400);
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
