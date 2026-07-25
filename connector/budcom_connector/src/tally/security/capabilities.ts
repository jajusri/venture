/**
 * Capability model for the live Tally read connector.
 *
 * SECURITY INVARIANT: only read/export capabilities exist. There is deliberately
 * NO RAW_XML, IMPORT, EXECUTE, FUNCTION, WRITE, CREATE, ALTER, DELETE, or UPDATE
 * capability anywhere in the production model. A capability that could mutate
 * Tally or run server-side logic cannot be named, therefore cannot be granted.
 */
export const TallyCapability = {
  HealthRead: 'TALLY_HEALTH_READ',
  CompanyRead: 'TALLY_COMPANY_READ',
  MasterRead: 'TALLY_MASTER_READ',
  ReportRead: 'TALLY_REPORT_READ',
} as const;

export type TallyCapability = (typeof TallyCapability)[keyof typeof TallyCapability];

/** The complete set of capabilities the production connector may ever hold. */
export const ALLOWED_CAPABILITIES: ReadonlySet<TallyCapability> = new Set([
  TallyCapability.HealthRead,
  TallyCapability.CompanyRead,
  TallyCapability.MasterRead,
  TallyCapability.ReportRead,
]);

/**
 * The only Tally request verb permitted at the egress boundary.
 * Exports are read-only; everything else can mutate data or run code.
 */
export const ONLY_ALLOWED_TALLY_REQUEST = 'EXPORT' as const;

/** Tally request payload kinds permitted for read/export. FUNCTION is excluded. */
export const ALLOWED_REQUEST_KINDS: ReadonlySet<string> = new Set([
  'DATA',
  'COLLECTION',
  'OBJECT',
]);

/**
 * Hard-blocked tokens. If any appears as a TALLYREQUEST/TYPE value the request is
 * rejected before XML is ever built or transported. These are write-capable or
 * server-execution verbs and must never leave the connector.
 */
export const FORBIDDEN_REQUEST_TOKENS: readonly string[] = [
  'IMPORT',
  'EXECUTE',
  'FUNCTION',
  'CREATE',
  'ALTER',
  'DELETE',
  'UPDATE',
  'INSERT',
  'POST',
  'SAVE',
  'MODIFY',
  'REMOVE',
  'CANCEL',
  'REWRITE',
  'SET',
];

export function isAllowedCapability(capability: string): capability is TallyCapability {
  return ALLOWED_CAPABILITIES.has(capability as TallyCapability);
}
