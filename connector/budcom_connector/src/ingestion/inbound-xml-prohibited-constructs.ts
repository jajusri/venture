import { ONLY_ALLOWED_TALLY_REQUEST } from '../tally/security/capabilities.js';
import { assertXmlContainsNoProhibitedMutationConstructs } from '../tally/safety/prohibited-mutation-xml.js';
import { InboundXmlReasonCode } from './inbound-xml-reason-codes.js';

export function assertInboundXmlProhibitedConstructs(rawXml: string): void {
  const trimmed = rawXml.trim();
  if (!trimmed) {
    throw prohibitedError(InboundXmlReasonCode.EmptyPayload, 'Inbound XML payload is empty.');
  }

  try {
    assertXmlContainsNoProhibitedMutationConstructs(trimmed, { requireExportRequest: false });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Inbound XML contains a prohibited construct.';
    const reasonCode = /DOCTYPE|entity declaration/i.test(message)
      ? InboundXmlReasonCode.ProhibitedConstruct
      : InboundXmlReasonCode.MutationInstruction;
    throw prohibitedError(reasonCode, message);
  }

  const tallyRequest = extractTagValue(trimmed, 'TALLYREQUEST')?.toUpperCase();
  if (tallyRequest && tallyRequest !== ONLY_ALLOWED_TALLY_REQUEST) {
    throw prohibitedError(
      InboundXmlReasonCode.MutationInstruction,
      'Inbound XML contains an unsupported Tally request instruction.',
    );
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
