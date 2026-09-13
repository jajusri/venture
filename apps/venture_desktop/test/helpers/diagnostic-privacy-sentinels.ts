/**
 * Test-only privacy sentinels used by diagnostic-privacy.test.ts to prove real-looking business
 * data never survives VENTURE's diagnostic sanitization/redaction pipeline. Moved out of
 * src/application/diagnostic-allowlist.ts (public-release hygiene audit, 2026-08-17): that module
 * compiles into apps/venture_desktop/dist/application/diagnostic-allowlist.js, which ships inside
 * the packaged Desktop installer — electron-builder.yml's `files` list excludes `*.test.*`/
 * `test/**`/`.env*` but not ordinary `src/` output, so these values (a real-looking company name,
 * a structurally-valid GSTIN, an address) had no reason to ship in every install. Neither export
 * was ever referenced by production code (main/renderer/preload) — only by this test file. Values
 * are unchanged from the original (some tests assert on their exact length for truncation checks).
 */

/** Sentinel values used by privacy absence tests — must never appear in exported diagnostics. */
export const DIAGNOSTIC_PRIVACY_SENTINELS = {
  companyName: 'JAJU SANITATIONS PRIVATE LIMITED',
  ledgerName: 'Sensitive Debtor Ledger',
  gstin: '29AABCU9603R1ZM',
  voucherNumber: 'VCH-2026-004821',
  phone: '+91-9876543210',
  email: 'finance@example-company.test',
  amount: '125000.50 Dr',
  xmlSnippet: '<LEDGER NAME="Secret Co"><AMOUNT>999.00</AMOUNT></LEDGER>',
  secretToken: 'sk-live-diagnostic-leak-test-token',
  windowsPath: 'C:\\Users\\ContosoUser\\Documents\\JAJU SANITATIONS\\venture.db',
  nestedCauseBody: '{"responseBody":"<STOCKITEM/>","headers":{"Authorization":"Bearer leak"}}',
  stockItemName: 'Sensitive Stock Item Alpha',
  postalAddress: '42 Industrial Estate, Bangalore 560001',
  licenceId: 'LIC-VENTURE-ENTERPRISE-998877',
  rawGuid: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
  rawAlterId: 'AlterID:48291',
  rawMasterId: 'MasterID:109283',
} as const;

export function assertDiagnosticOutputExcludesSentinels(
  serialized: string,
  extraSentinels: readonly string[] = [],
): void {
  const haystack = serialized.toLowerCase();
  const prohibited = [...Object.values(DIAGNOSTIC_PRIVACY_SENTINELS), ...extraSentinels];
  for (const sentinel of prohibited) {
    if (haystack.includes(sentinel.toLowerCase())) {
      throw new Error(`Prohibited diagnostic sentinel leaked: ${sentinel.slice(0, 32)}`);
    }
  }
}
