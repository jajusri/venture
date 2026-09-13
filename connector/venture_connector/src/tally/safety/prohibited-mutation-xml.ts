import { FORBIDDEN_REQUEST_TOKENS, ONLY_ALLOWED_TALLY_REQUEST } from '../security/capabilities.js';

/** Tag names that imply mutation or server-side execution when present anywhere in the envelope. */
export const PROHIBITED_MUTATION_TAG_NAMES: readonly string[] = [
  'IMPORT',
  'EXECUTE',
  'FUNCTION',
  'ALTER',
  'CREATE',
  'DELETE',
  'CANCEL',
  'REWRITE',
  'UPDATE',
  'SET',
  'INSERT',
  'POST',
  'SAVE',
  'MODIFY',
  'REMOVE',
];

export interface ProhibitedMutationScanOptions {
  readonly requireExportRequest?: boolean;
}

export function assertXmlContainsNoProhibitedMutationConstructs(
  rawXml: string,
  options: ProhibitedMutationScanOptions = {},
): void {
  const trimmed = rawXml.trim();
  if (!trimmed) {
    throw new ProhibitedMutationConstructError('XML payload is empty.');
  }

  if (/<!DOCTYPE/i.test(trimmed)) {
    throw new ProhibitedMutationConstructError('XML contains a prohibited DOCTYPE declaration.');
  }

  if (/<!ENTITY/i.test(trimmed)) {
    throw new ProhibitedMutationConstructError('XML contains a prohibited entity declaration.');
  }

  const tallyRequest = extractTagValue(trimmed, 'TALLYREQUEST')?.toUpperCase();
  if (tallyRequest) {
    for (const token of FORBIDDEN_REQUEST_TOKENS) {
      if (tallyRequest.includes(token)) {
        throw new ProhibitedMutationConstructError(
          `Prohibited mutation token "${token}" detected in TALLYREQUEST.`,
        );
      }
    }
    if (options.requireExportRequest && tallyRequest !== ONLY_ALLOWED_TALLY_REQUEST) {
      throw new ProhibitedMutationConstructError(
        `Only ${ONLY_ALLOWED_TALLY_REQUEST} requests are permitted; got TALLYREQUEST=${tallyRequest}.`,
      );
    }
  }

  const typeValue = extractTagValue(trimmed, 'TYPE')?.toUpperCase();
  if (typeValue) {
    for (const token of FORBIDDEN_REQUEST_TOKENS) {
      if (typeValue.includes(token)) {
        throw new ProhibitedMutationConstructError(
          `Prohibited mutation token "${token}" detected in TYPE.`,
        );
      }
    }
  }

  for (const tagName of PROHIBITED_MUTATION_TAG_NAMES) {
    if (containsProhibitedTag(trimmed, tagName)) {
      throw new ProhibitedMutationConstructError(
        `Prohibited mutation tag <${tagName}> detected in XML envelope.`,
      );
    }
  }
}

export class ProhibitedMutationConstructError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ProhibitedMutationConstructError';
  }
}

function containsProhibitedTag(xml: string, tagName: string): boolean {
  const pattern = new RegExp(`<\\s*(?:[A-Za-z_][\\w.-]*:)?${tagName}(?:\\s|>|/|\\?)`, 'i');
  return pattern.test(xml);
}

function extractTagValue(xml: string, tag: string): string | undefined {
  const pattern = new RegExp(`<(?:[A-Za-z_][\\w.-]*:)?${tag}>([\\s\\S]*?)<\\/(?:[A-Za-z_][\\w.-]*:)?${tag}>`, 'i');
  const match = xml.match(pattern);
  return match?.[1]?.trim();
}
