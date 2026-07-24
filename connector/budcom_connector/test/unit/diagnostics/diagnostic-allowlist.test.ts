import { describe, expect, it } from 'vitest';

import {
  CONNECTOR_DIAGNOSTIC_ALLOWLIST_VERSION,
  mapUnknownConnectorError,
  sanitizeConnectionDiagnostic,
  sanitizeConnectorDiagnosticText,
  sanitizeExtractorDiagnostic,
  sanitizeExtractorDiagnostics,
} from '../../../src/diagnostics/diagnostic-allowlist.js';
import type { ExtractorDiagnostics } from '../../../src/extraction/core/types.js';
import { MasterDataEntityType } from '../../../src/extraction/core/types.js';
import type { TallyDiagnosticsSnapshot } from '../../../src/tally/core/types.js';

const COMPANY = 'JAJU SANITATIONS PRIVATE LIMITED';
const LEDGER = 'Sensitive Debtor Ledger';
const STOCK_ITEM = 'Sensitive Stock Item Alpha';
const GSTIN = '29AABCU9603R1ZM';
const XML = '<LEDGER NAME="Secret Co"><AMOUNT>999.00</AMOUNT></LEDGER>';
const TOKEN = 'sk-live-diagnostic-leak-test-token';
const PATH = 'C:\\Users\\ContosoUser\\Documents\\JAJU SANITATIONS\\budcom.db';
const ADDRESS = '42 Industrial Estate, Bangalore 560001';
const LICENCE = 'LIC-BUDCOM-ENTERPRISE-998877';
const RAW_GUID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const ALTER_ID = 'AlterID:48291';
const MASTER_ID = 'MasterID:109283';

const APPROVED_CONNECTION_KEYS = [
  'state',
  'host',
  'port',
  'lastSuccessfulPingAt',
  'lastErrorAt',
  'lastErrorCode',
  'lastErrorMessage',
  'totalRequests',
  'failedRequests',
  'reconnectAttempts',
  'averageLatencyMs',
  'poolActiveConnections',
  'poolWaitingRequests',
  'safeMode',
  'circuitState',
  'lastRequest',
  'runtimeLimits',
] as const;

const APPROVED_EXTRACTOR_KEYS = [
  'entityType',
  'lastExtractedAt',
  'lastDurationMs',
  'lastItemCount',
  'lastErrorAt',
  'lastErrorMessage',
  'totalExtractions',
  'failedExtractions',
] as const;

function assertAbsent(serialized: string, prohibited: readonly string[]): void {
  const haystack = serialized.toLowerCase();
  for (const value of prohibited) {
    expect(haystack.includes(value.toLowerCase())).toBe(false);
  }
}

function assertExactKeys(value: Record<string, unknown>, allowed: readonly string[]): void {
  expect(Object.keys(value).sort()).toEqual([...allowed].sort());
}

describe('connector diagnostic allowlist', () => {
  it('sanitizes extractor lastErrorMessage', () => {
    const input: ExtractorDiagnostics = {
      entityType: MasterDataEntityType.Ledger,
      totalExtractions: 1,
      failedExtractions: 1,
      lastErrorMessage: `Failed for ${COMPANY} ledger ${LEDGER} gst ${GSTIN} xml ${XML}`,
    };
    const safe = sanitizeExtractorDiagnostic(input);
    const serialized = JSON.stringify(safe);
    assertAbsent(serialized, [COMPANY, LEDGER, GSTIN, XML]);
    expect(safe.lastErrorMessage).toBeTruthy();
    assertExactKeys(safe as unknown as Record<string, unknown>, APPROVED_EXTRACTOR_KEYS);
  });

  it('sanitizes connection diagnostic lastErrorMessage', () => {
    const snapshot: TallyDiagnosticsSnapshot = {
      state: 'degraded',
      host: '127.0.0.1',
      port: 9000,
      lastErrorMessage: `Timeout reading ${PATH} token ${TOKEN}`,
      totalRequests: 1,
      failedRequests: 1,
      reconnectAttempts: 0,
      averageLatencyMs: 10,
      poolActiveConnections: 0,
      poolWaitingRequests: 0,
      safeMode: false,
      circuitState: 'closed',
      runtimeLimits: {
        poolMaxConnections: 1,
        retryMaxAttempts: 1,
        minRequestIntervalMs: 0,
        maxRequestBytes: 1,
        maxResponseBytes: 1,
        circuitBreakerEnabled: false,
        timeoutMs: 1000,
      },
    };
    const safe = sanitizeConnectionDiagnostic(snapshot);
    const serialized = JSON.stringify(safe);
    assertAbsent(serialized, [PATH, TOKEN, 'ContosoUser']);
    expect(safe.host).toBe('127.0.0.1');
    expect(safe.port).toBe(9000);
    assertExactKeys(safe as unknown as Record<string, unknown>, APPROVED_CONNECTION_KEYS);
  });

  it('maps unknown errors without nested cause leakage', () => {
    const error = new Error(`company ${COMPANY}`);
    (error as Error & { cause?: unknown }).cause = {
      responseBody: XML,
      headers: { Authorization: `Bearer ${TOKEN}` },
    };
    const safe = mapUnknownConnectorError(error);
    const serialized = JSON.stringify(safe);
    assertAbsent(serialized, [COMPANY, XML, TOKEN]);
    expect(safe.message).toBe('An unexpected error occurred.');
    expect(safe.code).toBe('UNKNOWN_ERROR');
  });

  it('sanitizes free text sentinels', () => {
    const sanitized = sanitizeConnectorDiagnosticText(
      `ledger ${LEDGER} stock ${STOCK_ITEM} voucher VCH-1 phone +91-9876543210 email finance@example-company.test address ${ADDRESS} licence ${LICENCE} guid ${RAW_GUID} ${ALTER_ID} ${MASTER_ID}`,
    );
    assertAbsent(sanitized, [
      LEDGER,
      STOCK_ITEM,
      '9876543210',
      'finance@example-company.test',
      ADDRESS,
      LICENCE,
      RAW_GUID,
      ALTER_ID,
      MASTER_ID,
    ]);
  });

  it('drops unexpected properties from mocked internal extractor diagnostics', () => {
    const input = {
      entityType: MasterDataEntityType.StockItem,
      totalExtractions: 2,
      failedExtractions: 1,
      lastErrorMessage: `stock ${STOCK_ITEM} xml ${XML}`,
      companyName: COMPANY,
      rawXml: XML,
      nested: { secretToken: TOKEN },
    } as ExtractorDiagnostics & {
      companyName: string;
      rawXml: string;
      nested: { secretToken: string };
    };
    const safe = sanitizeExtractorDiagnostic(input);
    const serialized = JSON.stringify(safe);
    assertAbsent(serialized, [COMPANY, STOCK_ITEM, XML, TOKEN]);
    expect(Object.keys(safe as unknown as Record<string, unknown>)).not.toContain('companyName');
    expect(serialized).not.toContain('rawXml');
    expect(serialized).not.toContain('nested');
    assertExactKeys(safe as unknown as Record<string, unknown>, APPROVED_EXTRACTOR_KEYS);
  });

  it('drops unexpected properties from mocked internal connection diagnostics', () => {
    const snapshot = {
      state: 'connected',
      host: '127.0.0.1',
      port: 9000,
      totalRequests: 3,
      failedRequests: 0,
      reconnectAttempts: 0,
      averageLatencyMs: 12,
      poolActiveConnections: 1,
      poolWaitingRequests: 0,
      safeMode: false,
      circuitState: 'closed',
      runtimeLimits: {
        poolMaxConnections: 1,
        retryMaxAttempts: 1,
        minRequestIntervalMs: 0,
        maxRequestBytes: 1,
        maxResponseBytes: 1,
        circuitBreakerEnabled: false,
        timeoutMs: 1000,
      },
      companyName: COMPANY,
      rawResponseXml: XML,
      lastRequest: {
        correlationId: 'corr-1',
        sentAt: '2026-01-01T00:00:00.000Z',
        rawXml: XML,
      },
    } as TallyDiagnosticsSnapshot & {
      companyName: string;
      rawResponseXml: string;
      lastRequest: TallyDiagnosticsSnapshot['lastRequest'] & { rawXml: string };
    };
    const safe = sanitizeConnectionDiagnostic(snapshot);
    const serialized = JSON.stringify(safe);
    assertAbsent(serialized, [COMPANY, XML, 'rawResponseXml', 'rawXml']);
    assertExactKeys(safe as unknown as Record<string, unknown>, APPROVED_CONNECTION_KEYS);
    if (safe.lastRequest) {
      expect(Object.keys(safe.lastRequest)).not.toContain('rawXml');
      expect(safe.lastRequest.correlationId).toBe('corr-1');
    }
  });

  it('bounds extractor diagnostics list length', () => {
    const entries = Array.from({ length: 30 }, (_, index) => ({
      entityType: MasterDataEntityType.Ledger,
      totalExtractions: index,
      failedExtractions: 0,
    }));
    const safe = sanitizeExtractorDiagnostics(entries);
    expect(safe.length).toBeLessThanOrEqual(20);
  });

  it('preserves approved operational metadata in sanitized extractor output', () => {
    const input: ExtractorDiagnostics = {
      entityType: MasterDataEntityType.Ledger,
      totalExtractions: 4,
      failedExtractions: 1,
      lastDurationMs: 120,
      lastItemCount: 923,
      lastExtractedAt: '2026-01-01T00:00:00.000Z',
    };
    const safe = sanitizeExtractorDiagnostic(input);
    expect(safe.entityType).toBe(MasterDataEntityType.Ledger);
    expect(safe.totalExtractions).toBe(4);
    expect(safe.failedExtractions).toBe(1);
    expect(safe.lastDurationMs).toBe(120);
    expect(safe.lastItemCount).toBe(923);
    expect(CONNECTOR_DIAGNOSTIC_ALLOWLIST_VERSION).toBe(1);
  });
});
