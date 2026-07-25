import { FORBIDDEN_REQUEST_TOKENS } from '../tally/security/capabilities.js';
import { InboundXmlReasonCode } from './inbound-xml-reason-codes.js';

export function assertInboundXmlProhibitedConstructs(rawXml: string): void {
  const trimmed = rawXml.trim();
  if (!trimmed) {
    throw prohibitedError(InboundXmlReasonCode.EmptyPayload, 'Inbound XML payload is empty.');
  }

  if (/<!DOCTYPE/i.test(trimmed)) {
    throw prohibitedError(
      InboundXmlReasonCode.ProhibitedConstruct,
      'Inbound XML contains a prohibited DOCTYPE declaration.',
    );
  }

  if (/<!ENTITY/i.test(trimmed)) {
    throw prohibitedError(
      InboundXmlReasonCode.ProhibitedConstruct,
      'Inbound XML contains a prohibited entity declaration.',
    );
  }

  assertNoMutationInstructions(trimmed);
}

function assertNoMutationInstructions(rawXml: string): void {
  const tallyRequest = extractTagValue(rawXml, 'TALLYREQUEST')?.toUpperCase();
  if (tallyRequest) {
    for (const token of FORBIDDEN_REQUEST_TOKENS) {
      if (tallyRequest.includes(token)) {
        throw prohibitedError(
          InboundXmlReasonCode.MutationInstruction,
          'Inbound XML contains a prohibited mutation instruction.',
        );
      }
    }
    if (tallyRequest !== 'EXPORT') {
      throw prohibitedError(
        InboundXmlReasonCode.MutationInstruction,
        'Inbound XML contains an unsupported Tally request instruction.',
      );
    }
  }

  const upper = rawXml.toUpperCase();
  for (const token of ['<IMPORT ', '<EXECUTE', '<FUNCTION ']) {
    if (upper.includes(token)) {
      throw prohibitedError(
        InboundXmlReasonCode.MutationInstruction,
        'Inbound XML contains a prohibited mutation instruction.',
      );
    }
  }
}

function extractTagValue(xml: string, tag: string): string | undefined {
  const pattern = new RegExp(`<${tag}>([\\s\\S]*?)<\\/${tag}>`, 'i');
  const match = xml.match(pattern);
  return match?.[1]?.trim();
}

function prohibitedError(reasonCode: InboundXmlReasonCode, message: string): Error {
  const error = new Error(message);
  (error as Error & { reasonCode: InboundXmlReasonCode }).reasonCode = reasonCode;
  return error;
}
