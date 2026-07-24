export const PERSISTENT_TEXT_LIMITS = {
  maxStringLength: 500,
} as const;

const REDACTED = '[REDACTED]';
const TRUNCATED_SUFFIX = '… [truncated]';

const GSTIN_PATTERN = /\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\b/gi;
const PHONE_PATTERN = /\b(?:\+?\d{1,3}[-.\s]?)?(?:\(?\d{2,4}\)?[-.\s]?)?\d{6,12}\b/g;
const AMOUNT_PATTERN = /\b\d{1,3}(?:,\d{3})*(?:\.\d{1,4})?\s*(?:Dr|Cr|INR|Rs\.?)?\b/gi;
const XML_BLOCK_PATTERN = /<[A-Za-z!?][^>]*>[\s\S]*?<\/[A-Za-z][^>]*>/gi;
const XML_SELF_CLOSING_PATTERN = /<[A-Za-z!?][^>]*\/>/gi;
const XML_TAG_PATTERN = /<\/?[A-Z][A-Z0-9._:-]*/gi;
const WINDOWS_PATH_PATTERN = /[A-Za-z]:\\(?:[^\\:*?"<>|\r\n]+\\)+[^\\:*?"<>|\r\n]*/g;
const UNIX_PATH_PATTERN = /\/(?:home|Users|tmp|var|private)\/[^\s"'<>]+/gi;
const BEARER_HEADER_PATTERN = /authorization\s*:\s*\S+/gi;
const API_TOKEN_PATTERN = /\b(?:sk|pk|api)[-_][A-Za-z0-9_-]{8,}\b/gi;
const INLINE_TOKEN_PATTERN = /\btoken\s+[A-Za-z0-9._-]{8,}\b/gi;
const EMAIL_PATTERN = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi;
const VOUCHER_PATTERN = /\bVCH[-\s]?[0-9A-Z-]+\b/gi;
const COMPANY_LEGAL_NAME_PATTERN =
  /\b[A-Z][A-Z0-9&.'-]*(?:\s+[A-Z0-9][A-Z0-9&.'-]*)*\s+(?:PRIVATE LIMITED|PVT\.?\s*LTD\.?|LIMITED|LLP|INC\.?|CORP\.?)\b/g;
const FOR_CLAUSE_PATTERN = /\bfor\s+[^,.;\n]+/gi;
const LEDGER_NAME_PATTERN = /\b[A-Za-z0-9][A-Za-z0-9\s&.'-]{1,}\s+Ledger\b/gi;
const STOCK_ITEM_NAME_PATTERN = /\b[A-Za-z0-9][A-Za-z0-9\s&.'-]{1,}\s+Stock Item(?:\s+[A-Za-z0-9][A-Za-z0-9\s&.'-]*)?\b/gi;
const ENTITY_LABEL_PATTERN = /\b(?:ledger|party|customer|supplier|stock item|company|debtor|creditor)\s*:\s*[^,.;\n]+/gi;
const GUID_PATTERN = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
const GUID_PREFIX_PATTERN = /\bguid:[0-9a-f-]+/gi;
const ALTER_MASTER_ID_PATTERN = /\b(?:AlterID|MasterID)\s*[:=]?\s*\d+\b/gi;
const LICENCE_PATTERN = /\b(?:LIC|LICENCE|LICENSE)[-_][A-Z0-9-]{6,}\b/gi;
const ADDRESS_PATTERN = /\b\d{1,4}\s+[A-Za-z0-9\s,.-]{5,}\b(?:\d{5,6})?\b/g;
const SECRET_VALUE_PATTERN = /\b(password|token|secret|api[_-]?key)\s*[:=]\s*\S+/gi;

/** Neutralize control characters that could forge JSONL or log records. */
export function neutralizePersistentControlCharacters(value: string): string {
  // eslint-disable-next-line no-control-regex -- intentional neutralization of log injection characters
  return value.replace(/[\u0000-\u001f\u007f]/g, ' ');
}

export function sanitizePersistentText(
  value: string,
  maxLength: number = PERSISTENT_TEXT_LIMITS.maxStringLength,
): string {
  let result = neutralizePersistentControlCharacters(value);
  result = result.replace(BEARER_HEADER_PATTERN, `Authorization: ${REDACTED}`);
  result = result.replace(API_TOKEN_PATTERN, REDACTED);
  result = result.replace(INLINE_TOKEN_PATTERN, `token ${REDACTED}`);
  result = result.replace(SECRET_VALUE_PATTERN, (match) => `${match.split(/[:=]/)[0] ?? 'secret'}=[REDACTED]`);
  result = result.replace(EMAIL_PATTERN, REDACTED);
  result = result.replace(COMPANY_LEGAL_NAME_PATTERN, REDACTED);
  result = result.replace(FOR_CLAUSE_PATTERN, 'for [REDACTED]');
  result = result.replace(LEDGER_NAME_PATTERN, REDACTED);
  result = result.replace(STOCK_ITEM_NAME_PATTERN, REDACTED);
  result = result.replace(ENTITY_LABEL_PATTERN, (match) => `${match.split(':')[0]}: ${REDACTED}`);
  result = result.replace(VOUCHER_PATTERN, REDACTED);
  result = result.replace(GSTIN_PATTERN, REDACTED);
  result = result.replace(XML_BLOCK_PATTERN, REDACTED);
  result = result.replace(XML_SELF_CLOSING_PATTERN, REDACTED);
  result = result.replace(XML_TAG_PATTERN, REDACTED);
  result = result.replace(WINDOWS_PATH_PATTERN, REDACTED);
  result = result.replace(UNIX_PATH_PATTERN, REDACTED);
  result = result.replace(PHONE_PATTERN, REDACTED);
  result = result.replace(AMOUNT_PATTERN, REDACTED);
  result = result.replace(GUID_PREFIX_PATTERN, REDACTED);
  result = result.replace(GUID_PATTERN, REDACTED);
  result = result.replace(ALTER_MASTER_ID_PATTERN, REDACTED);
  result = result.replace(LICENCE_PATTERN, REDACTED);
  result = result.replace(ADDRESS_PATTERN, REDACTED);
  if (result.length > maxLength) {
    return `${result.slice(0, maxLength - TRUNCATED_SUFFIX.length)}${TRUNCATED_SUFFIX}`;
  }
  return result;
}
