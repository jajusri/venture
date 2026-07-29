import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import type { ErpTransport } from '../../../src/tally/core/erp-transport.interface.js';
import type {
  ErpTransportRequest,
  ErpTransportResponse,
} from '../../../src/tally/core/types.js';
import type { VoucherDiscoveryManifest } from '../../../src/discovery/voucher-fixture-manifest.js';
import {
  executeVoucherDiscovery,
  type ExecuteVoucherDiscoveryOptions,
} from '../../../src/discovery/voucher-discovery-executor.js';
import {
  buildVoucherDiscoveryRequest,
  VOUCHER_DISCOVERY_FETCH_METHODS,
} from '../../../src/tally/discovery/voucher-discovery-request.js';
import {
  VOUCHER_DISCOVERY_COLLECTION_NAME,
  VOUCHER_DISCOVERY_OPERATION_ID,
} from '../../../src/tally/registry/voucher-discovery-operation.js';
import { findApprovedOperationByRequest } from '../../../src/tally/registry/operation-registry.js';
import { PROHIBITED_MUTATION_TAG_NAMES } from '../../../src/tally/safety/prohibited-mutation-xml.js';
import { validateTallyRequestXml } from '../../../src/tally/safety/xml-request-validator.js';

const temporaryRoots: string[] = [];

afterEach(() => {
  for (const temporaryRoot of temporaryRoots.splice(0)) {
    if (path.basename(temporaryRoot).startsWith('budcom-voucher-discovery-')) {
      fs.rmSync(temporaryRoot, { recursive: true, force: true });
    }
  }
});

function approvedManifest(): VoucherDiscoveryManifest {
  return {
    evidenceVersion: 'test-evidence-v1',
    governanceMode: 'single-authorized-owner',
    authorizedOwner: {
      name: 'Test Owner',
      role: 'Project Owner',
      approvedAt: '2026-07-26T14:30:00Z',
      approvalReference: 'TEST-APPROVAL-1',
      fixtureOnlyAcknowledged: true,
      syntheticDataConfirmed: true,
      backupConfirmed: true,
      sanitizationApproved: true,
      dateRangeApproved: true,
      expectedCountsApproved: true,
      executionAcknowledged: true,
    },
    fixtureCompanyAlias: 'FIXTURE_ONLY',
    nonProductionConfirmed: true,
    approvedExperiments: [],
    dateRange: { dateFrom: '2026-07-01', dateTo: '2026-07-26' },
    expectedVouchers: [],
    expectedCountsByType: { Sales: 2 },
    expectedActiveCount: 2,
    expectedCancelledCount: 0,
    mutationSequence: [],
    draftValuesApprovalStatus: 'owner-approved',
    sanitizationStatus: 'sanitized',
    reviewerSignOffStatus: 'approved',
  };
}

function responseXml(): string {
  return `<ENVELOPE>
  <BODY>
    <DESC><CMPINFO><VOUCHER>999</VOUCHER></CMPINFO></DESC>
    <DATA>
      <COLLECTION>
        <VOUCHER>
          <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
          <GUID>SENSITIVE-GUID-ONE</GUID>
          <MASTERID>991</MASTERID>
          <ALTERID>44</ALTERID>
          <VOUCHERNUMBER>SECRET-VCH-001</VOUCHERNUMBER>
          <PARTYLEDGERNAME>PRIVATE PARTY</PARTYLEDGERNAME>
          <NARRATION>PRIVATE NARRATION</NARRATION>
          <AMOUNT>-12345.67</AMOUNT>
          <ISDELETED>NO</ISDELETED>
          <ALLLEDGERENTRIES.LIST><LEDGERNAME>PRIVATE LEDGER</LEDGERNAME><AMOUNT>12345.67</AMOUNT></ALLLEDGERENTRIES.LIST>
        </VOUCHER>
        <VOUCHER>
          <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
          <GUID>SENSITIVE-GUID-TWO</GUID>
          <MASTERID>992</MASTERID>
          <ALTERID>45</ALTERID>
          <VOUCHERNUMBER>SECRET-VCH-002</VOUCHERNUMBER>
          <PARTYLEDGERNAME>OTHER PRIVATE PARTY</PARTYLEDGERNAME>
          <REFERENCE>PRIVATE REFERENCE</REFERENCE>
          <AMOUNT>999.00</AMOUNT>
          <INVENTORYENTRIES.LIST><STOCKITEMNAME>PRIVATE ITEM</STOCKITEMNAME><ACTUALQTY>7 PCS</ACTUALQTY></INVENTORYENTRIES.LIST>
        </VOUCHER>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;
}

class FakeTransport implements ErpTransport {
  calls = 0;
  requests: ErpTransportRequest[] = [];

  constructor(
    private readonly responseBody = responseXml(),
    private readonly failure?: Error,
  ) {}

  async send(request: ErpTransportRequest): Promise<ErpTransportResponse> {
    this.calls += 1;
    this.requests.push(request);
    if (this.failure) throw this.failure;
    return {
      body: this.responseBody,
      statusCode: 200,
      durationMs: 12,
      headers: {},
    };
  }

  async close(): Promise<void> {}
}

function executionSetup(
  overrides: Partial<ExecuteVoucherDiscoveryOptions> = {},
): { options: ExecuteVoucherDiscoveryOptions; root: string } {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-voucher-discovery-'));
  temporaryRoots.push(root);
  const evidenceRoot = path.join(root, 'evidence');
  const restrictedRoot = path.join(root, 'restricted');
  const repositoryRoot = path.join(root, 'repository');
  fs.mkdirSync(evidenceRoot);
  fs.mkdirSync(restrictedRoot);
  fs.mkdirSync(repositoryRoot);
  const options: ExecuteVoucherDiscoveryOptions = {
    fixtureManifestPath: path.join(root, 'manifest.json'),
    manifest: approvedManifest(),
    operatorCompany: 'FIXTURE_ONLY',
    productionCompanyNames: ['PRODUCTION_ONLY'],
    sanitizationMode: 'strict',
    maximumResponseBytes: 1_048_576,
    timeoutMs: 30_000,
    maxXmlDepth: 64,
    maxXmlNodeCount: 32_768,
    reviewerAcknowledgment: 'TEST-REVIEWER-ACK',
    acknowledgeFixtureOnly: true,
    experimentLabel: 'baseline',
    approvedOutputRoot: evidenceRoot,
    outputDirectory: path.join(evidenceRoot, 'run-1'),
    restrictedOutputRoot: restrictedRoot,
    restrictedOutputDirectory: path.join(restrictedRoot, 'run-1'),
    repositoryRoot,
    ...overrides,
  };
  return { options, root };
}

describe('executable Voucher discovery harness', () => {
  it('builds the BASELINE-04 read-only embedded Voucher collection request', () => {
    const request = buildVoucherDiscoveryRequest({
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      companyName: 'ESTIMATION',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-26',
    });

    expect(request.xml).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(request.xml).toMatch(
      /<HEADER>[\s\S]*<TYPE>Collection<\/TYPE>[\s\S]*<ID>Budcom Voucher Discovery<\/ID>[\s\S]*<\/HEADER>/,
    );
    expect(request.xml).toContain(
      `<COLLECTION NAME="${VOUCHER_DISCOVERY_COLLECTION_NAME}" ISMODIFY="No" ISFIXED="No" ISINITIALIZE="Yes">`,
    );
    expect(request.xml).toMatch(
      /<COLLECTION NAME="Budcom Voucher Discovery"[\s\S]*?<TYPE>Voucher<\/TYPE>[\s\S]*?<\/COLLECTION>/,
    );
    expect(request.xml).toContain('<SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>');
    expect(request.xml).toContain('<SVCURRENTCOMPANY>ESTIMATION</SVCURRENTCOMPANY>');
    expect(request.xml).toContain('<SVFROMDATE>20260701</SVFROMDATE>');
    expect(request.xml).toContain('<SVTODATE>20260726</SVTODATE>');
    expect(request.xml).not.toContain('<ID>Day Book</ID>');
    expect(request.xml).not.toContain('<FILTER>');
    expect(request.xml).not.toMatch(/<NATIVEMETHOD>[^<]*\*/);
  });

  it('emits every BASELINE-04 fetch exactly once and in order', () => {
    const xml = buildVoucherDiscoveryRequest({
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      companyName: 'ESTIMATION',
      dateFrom: '2026-07-27',
      dateTo: '2026-07-27',
    }).xml;

    let previousIndex = -1;
    for (const method of VOUCHER_DISCOVERY_FETCH_METHODS) {
      const element = `<NATIVEMETHOD>${method}</NATIVEMETHOD>`;
      expect(xml.split(element)).toHaveLength(2);
      const index = xml.indexOf(element);
      expect(index).toBeGreaterThan(previousIndex);
      previousIndex = index;
    }
    expect(xml.match(/<NATIVEMETHOD>/g)).toHaveLength(
      VOUCHER_DISCOVERY_FETCH_METHODS.length,
    );
    expect(xml).not.toMatch(/<NATIVEMETHOD>[^<]*\*/);
  });

  it('contains no prohibited write or execution tags', () => {
    const xml = buildVoucherDiscoveryRequest({
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      companyName: 'ESTIMATION',
      dateFrom: '2026-07-27',
      dateTo: '2026-07-27',
    }).xml;

    for (const tag of PROHIBITED_MUTATION_TAG_NAMES) {
      expect(xml).not.toMatch(new RegExp(`<${tag}(?:>|\\s)`, 'i'));
    }
    expect(validateTallyRequestXml(xml, 65_536)).toEqual({
      requestType: 'EXPORT',
      requestKind: 'COLLECTION',
      collectionId: VOUCHER_DISCOVERY_COLLECTION_NAME,
    });
  });

  it('retains XML escaping in canonical discovery static variables', () => {
    const request = buildVoucherDiscoveryRequest({
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      companyName: 'Fixture & <Test> "Company"',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-26',
    });

    expect(request.xml).toContain(
      '<SVCURRENTCOMPANY>Fixture &amp; &lt;Test&gt; &quot;Company&quot;</SVCURRENTCOMPANY>',
    );
    expect(request.xml).not.toContain('Fixture & <Test> "Company"');
  });

  it('sends exactly one bounded read-only request and writes only approved evidence', async () => {
    const { options } = executionSetup();
    const transport = new FakeTransport();
    const result = await executeVoucherDiscovery(options, transport);

    expect(transport.calls).toBe(1);
    expect(transport.requests[0]?.timeoutMs).toBe(30_000);
    expect(result).toMatchObject({
      resultState: 'candidate-complete',
      observedVoucherCount: 2,
    });
    expect(result.evidenceFiles).toEqual([
      'request-shape.json',
      'response-structure.xml',
      'field-inventory.json',
      'structural-counts.json',
      'performance.json',
      'completeness-observations.md',
      'privacy-review.json',
      'run-metadata.json',
    ]);
    expect(fs.readdirSync(options.outputDirectory).sort()).toEqual(
      [...result.evidenceFiles].sort(),
    );
    expect(fs.readdirSync(options.restrictedOutputDirectory)).toEqual([
      'identity-fingerprints.local.json',
    ]);
  });

  it('counts only DATA/COLLECTION Voucher records and ignores CMPINFO/VOUCHER', async () => {
    const { options } = executionSetup();
    const result = await executeVoucherDiscovery(options, new FakeTransport());
    const structuralCounts = JSON.parse(
      fs.readFileSync(path.join(options.outputDirectory, 'structural-counts.json'), 'utf8'),
    ) as {
      observedVoucherCount: number;
      observedVoucherTypeSummary: {
        distinctVoucherTypeCount: number;
        perType: Array<{ identifierHash: string; recordCount: number }>;
      };
    };

    expect(result.observedVoucherCount).toBe(2);
    expect(structuralCounts.observedVoucherCount).toBe(2);
    expect(structuralCounts.observedVoucherTypeSummary).toMatchObject({
      distinctVoucherTypeCount: 1,
      perType: [{ recordCount: 2 }],
    });
  });

  it('persists only hashed voucher-type identifiers and reconciles per-type counts', async () => {
    const { options } = executionSetup();
    const result = await executeVoucherDiscovery(options, new FakeTransport());
    const structuralCounts = fs.readFileSync(
      path.join(options.outputDirectory, 'structural-counts.json'),
      'utf8',
    );
    const completeness = fs.readFileSync(
      path.join(options.outputDirectory, 'completeness-observations.md'),
      'utf8',
    );

    expect(result.resultState).toBe('candidate-complete');
    expect(structuralCounts).not.toContain('Sales');
    expect(structuralCounts).toMatch(/"identifierHash": "[a-f0-9]{64}"/);
    expect(completeness).toContain('Voucher-type counts reconciled: yes');
  });

  it('marks discovery incomplete when privacy-safe per-type counts differ', async () => {
    const { options } = executionSetup({
      manifest: {
        ...approvedManifest(),
        expectedCountsByType: { Sales: 1, Receipt: 1 },
      },
    });

    const result = await executeVoucherDiscovery(options, new FakeTransport());
    expect(result).toMatchObject({
      resultState: 'incomplete',
      observedVoucherCount: 2,
    });
  });

  it('redacts sensitive sentinel values and never persists raw XML', async () => {
    const { options } = executionSetup();
    await executeVoucherDiscovery(options, new FakeTransport());
    const committedEvidence = fs
      .readdirSync(options.outputDirectory)
      .map((file) => fs.readFileSync(path.join(options.outputDirectory, file), 'utf8'))
      .join('\n');

    for (const sentinel of [
      'SENSITIVE-GUID',
      'SECRET-VCH',
      'PRIVATE PARTY',
      'PRIVATE NARRATION',
      'PRIVATE LEDGER',
      'PRIVATE REFERENCE',
      'PRIVATE ITEM',
      'Sales',
      '12345.67',
      '999.00',
      '7 PCS',
    ]) {
      expect(committedEvidence).not.toContain(sentinel);
    }
    expect(committedEvidence).not.toContain(responseXml());
    expect(committedEvidence).toContain('[REDACTED]');
  });

  it.each([
    ['incomplete owner approval', { manifest: { ...approvedManifest(), authorizedOwner: { ...approvedManifest().authorizedOwner!, executionAcknowledged: false } } }],
    ['production company', { productionCompanyNames: ['FIXTURE_ONLY'] }],
    ['company mismatch', { operatorCompany: 'OTHER_FIXTURE' }],
    ['missing CLI acknowledgment', { acknowledgeFixtureOnly: false }],
    ['disabled sanitization', { sanitizationMode: 'disabled' }],
    ['missing response limit', { maximumResponseBytes: 0 }],
    ['missing timeout', { timeoutMs: 0 }],
    ['reversed date range', { manifest: { ...approvedManifest(), dateRange: { dateFrom: '2026-07-27', dateTo: '2026-07-01' } } }],
    ['excessive date range', { manifest: { ...approvedManifest(), dateRange: { dateFrom: '2025-01-01', dateTo: '2026-01-02' } } }],
  ] as const)('fails before network for %s', async (_label, override) => {
    const { options } = executionSetup(override as Partial<ExecuteVoucherDiscoveryOptions>);
    const transport = new FakeTransport();
    await expect(executeVoucherDiscovery(options, transport)).rejects.toThrow();
    expect(transport.calls).toBe(0);
  });

  it('rejects unsafe evidence roots before network', async () => {
    const { options, root } = executionSetup();
    const transport = new FakeTransport();
    await expect(
      executeVoucherDiscovery(
        { ...options, outputDirectory: path.join(root, 'outside', 'run') },
        transport,
      ),
    ).rejects.toThrow(/approved root/i);
    expect(transport.calls).toBe(0);
  });

  it('fails closed on timeout and writes no evidence', async () => {
    const { options } = executionSetup();
    const transport = new FakeTransport('', new Error('timeout'));
    await expect(executeVoucherDiscovery(options, transport)).rejects.toThrow('timeout');
    expect(transport.calls).toBe(1);
    expect(fs.existsSync(options.outputDirectory)).toBe(false);
  });

  it('fails closed on oversized response and writes no evidence', async () => {
    const { options } = executionSetup({ maximumResponseBytes: 64 });
    const transport = new FakeTransport(responseXml());
    await expect(executeVoucherDiscovery(options, transport)).rejects.toThrow(/response-size limit/);
    expect(transport.calls).toBe(1);
    expect(fs.existsSync(options.outputDirectory)).toBe(false);
  });

  it('enforces XML depth and node-count limits', async () => {
    const depthSetup = executionSetup({ maxXmlDepth: 2 });
    await expect(
      executeVoucherDiscovery(depthSetup.options, new FakeTransport()),
    ).rejects.toThrow(/nesting depth/);

    const nodeSetup = executionSetup({ maxXmlNodeCount: 2 });
    await expect(
      executeVoucherDiscovery(nodeSetup.options, new FakeTransport()),
    ).rejects.toThrow(/node count/);
  });

  it('permits only the discovery candidate while the proven shape resolves to production VOUCHERS', () => {
    expect(() =>
      buildVoucherDiscoveryRequest({
        operationId: 'OTHER' as typeof VOUCHER_DISCOVERY_OPERATION_ID,
        companyName: 'FIXTURE_ONLY',
        dateFrom: '2026-07-01',
        dateTo: '2026-07-26',
      }),
    ).toThrow(/only the approved/i);
    expect(
      findApprovedOperationByRequest('COLLECTION', VOUCHER_DISCOVERY_COLLECTION_NAME),
    ).toMatchObject({ operationId: 'VOUCHERS' });
  });

  it('rejects mutation-shaped XML through the shared request validator', () => {
    expect(() =>
      validateTallyRequestXml(
        '<ENVELOPE><HEADER><TALLYREQUEST>Import</TALLYREQUEST><TYPE>Data</TYPE><ID>Day Book</ID></HEADER></ENVELOPE>',
        65_536,
      ),
    ).toThrow(/prohibited|forbidden|only export/i);
  });

  it('does not add public Voucher routes or IPC', async () => {
    const apiStubs = await import('../../../src/api/routes/api-stubs.js');
    expect(Object.keys(apiStubs)).not.toContain('registerVoucherRoutes');
    const tokens = await import('../../../src/core/tokens.js');
    expect(Object.values(tokens.ServiceTokens)).not.toContain('VoucherDiscovery');
  });
});
