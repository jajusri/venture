import { createHash } from 'node:crypto';

import { InboundXmlReasonCode } from './inbound-xml-reason-codes.js';

export interface DecodedInboundXml {
  readonly rawBytes: Buffer;
  readonly text: string;
  readonly detectedEncoding: 'utf-8';
  readonly hadBom: boolean;
}

const UTF8_BOM = Buffer.from([0xef, 0xbb, 0xbf]);

export function decodeInboundXmlBytes(rawBytes: Buffer): DecodedInboundXml {
  if (rawBytes.length === 0) {
    throw inboundEncodingError(InboundXmlReasonCode.EmptyPayload, 'Inbound XML payload is empty.');
  }

  if (rawBytes.includes(0x00)) {
    throw inboundEncodingError(
      InboundXmlReasonCode.BinaryNullByte,
      'Inbound XML payload contains prohibited null bytes.',
    );
  }

  let payload = rawBytes;
  let hadBom = false;
  if (rawBytes.subarray(0, 3).equals(UTF8_BOM)) {
    hadBom = true;
    payload = rawBytes.subarray(3);
  }

  const declaration = payload.subarray(0, Math.min(payload.length, 256)).toString('utf8');
  const encodingMatch = declaration.match(/<\?xml[^>]*encoding=['"]([^'"]+)['"]/i);
  if (encodingMatch) {
    const declared = encodingMatch[1]!.trim().toLowerCase();
    if (declared !== 'utf-8' && declared !== 'utf8') {
      throw inboundEncodingError(
        InboundXmlReasonCode.UnsupportedEncoding,
        'Inbound XML declares an unsupported encoding.',
      );
    }
  }

  let text: string;
  try {
    text = payload.toString('utf8');
  } catch {
    throw inboundEncodingError(
      InboundXmlReasonCode.InvalidByteSequence,
      'Inbound XML contains invalid UTF-8 byte sequences.',
    );
  }

  if (text.includes('\u0000')) {
    throw inboundEncodingError(
      InboundXmlReasonCode.BinaryNullByte,
      'Inbound XML payload contains prohibited null bytes.',
    );
  }

  if (!isValidUtf8RoundTrip(payload, text)) {
    throw inboundEncodingError(
      InboundXmlReasonCode.InvalidByteSequence,
      'Inbound XML contains invalid UTF-8 byte sequences.',
    );
  }

  return {
    rawBytes,
    text,
    detectedEncoding: 'utf-8',
    hadBom,
  };
}

export function fingerprintInboundXmlBytes(rawBytes: Buffer): string {
  return createHash('sha256').update(rawBytes).digest('hex');
}

function isValidUtf8RoundTrip(payload: Buffer, text: string): boolean {
  return Buffer.byteLength(text, 'utf8') === payload.length;
}

function inboundEncodingError(reasonCode: InboundXmlReasonCode, message: string): Error {
  const error = new Error(message);
  (error as Error & { reasonCode: InboundXmlReasonCode }).reasonCode = reasonCode;
  return error;
}

export function extractReasonCode(error: unknown): string | undefined {
  if (typeof error === 'object' && error !== null && 'reasonCode' in error) {
    const value = (error as { reasonCode?: unknown }).reasonCode;
    return typeof value === 'string' ? value : undefined;
  }
  return undefined;
}
