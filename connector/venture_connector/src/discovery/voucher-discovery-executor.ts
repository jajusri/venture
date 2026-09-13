import {
  countParsedXmlNodes,
  measureParsedXmlMaxDepth,
  TallyXmlResponseParser,
} from '../tally/xml/response-parser.js';
import {
  APPROVED_XML_PARSER_MAX_BYTES,
  APPROVED_XML_PARSER_MAX_DEPTH,
  APPROVED_XML_PARSER_MAX_NODE_COUNT,
} from '../tally/xml/response-parser-limits.js';
import type { ErpTransport } from '../tally/core/erp-transport.interface.js';
import type { VoucherDiscoveryManifest } from './voucher-fixture-manifest.js';
import { validateVoucherDiscoveryManifest } from './voucher-fixture-manifest.js';
import {
  VOUCHER_DISCOVERY_OPERATION_ID,
  validateVoucherDiscoveryRequestShape,
} from '../tally/registry/voucher-discovery-operation.js';
import {
  buildVoucherDiscoveryRequest,
  VOUCHER_DISCOVERY_MAX_REQUEST_BYTES,
} from '../tally/discovery/voucher-discovery-request.js';
import {
  sanitizeVoucherResponseStructure,
  voucherTypeIdentifierHash,
  type PrivacySafeVoucherTypeSummary,
} from './voucher-evidence-sanitizer.js';
import {
  validateVoucherEvidenceLocations,
  writeVoucherDiscoveryEvidence,
  type VoucherEvidenceLocations,
} from './voucher-evidence-writer.js';
import { nonOperationalVoucherDiscoveryHarness } from './voucher-discovery-harness.js';

export const VOUCHER_DISCOVERY_EXPERIMENT_LABELS = [
  'baseline',
  'narration-edit',
  'amount-edit',
  'renumber',
  'cancel',
  'restore',
  'restart',
  'reopen',
  'delete',
] as const;

export type VoucherDiscoveryExperimentLabel =
  (typeof VOUCHER_DISCOVERY_EXPERIMENT_LABELS)[number];

export interface ExecuteVoucherDiscoveryOptions extends VoucherEvidenceLocations {
  readonly fixtureManifestPath: string;
  readonly manifest: VoucherDiscoveryManifest;
  readonly operatorCompany: string;
  readonly productionCompanyNames: readonly string[];
  readonly sanitizationMode: string;
  readonly maximumResponseBytes: number;
  readonly timeoutMs: number;
  readonly maxXmlDepth: number;
  readonly maxXmlNodeCount: number;
  readonly reviewerAcknowledgment: string;
  readonly acknowledgeFixtureOnly: boolean;
  readonly experimentLabel: VoucherDiscoveryExperimentLabel;
}

export interface VoucherDiscoveryExecutionResult {
  readonly resultState: 'candidate-complete' | 'incomplete' | 'unknown';
  readonly responseBytes: number;
  readonly durationMs: number;
  readonly observedVoucherCount: number;
  readonly evidenceFiles: readonly string[];
}

function expectedVoucherCount(manifest: VoucherDiscoveryManifest): number {
  return Object.values(manifest.expectedCountsByType).reduce((sum, count) => sum + count, 0);
}

function expectedVoucherTypeSummary(
  manifest: VoucherDiscoveryManifest,
): PrivacySafeVoucherTypeSummary {
  return {
    distinctVoucherTypeCount: Object.keys(manifest.expectedCountsByType).length,
    unidentifiedVoucherTypeCount: 0,
    perType: Object.entries(manifest.expectedCountsByType)
      .map(([voucherType, recordCount]) => ({
        identifierHash: voucherTypeIdentifierHash(voucherType),
        recordCount,
      }))
      .sort((left, right) => left.identifierHash.localeCompare(right.identifierHash)),
  };
}

function voucherTypeSummariesMatch(
  expected: PrivacySafeVoucherTypeSummary,
  observed: PrivacySafeVoucherTypeSummary,
): boolean {
  return (
    observed.unidentifiedVoucherTypeCount === 0 &&
    expected.distinctVoucherTypeCount === observed.distinctVoucherTypeCount &&
    expected.perType.length === observed.perType.length &&
    expected.perType.every(
      (entry, index) =>
        entry.identifierHash === observed.perType[index]?.identifierHash &&
        entry.recordCount === observed.perType[index]?.recordCount,
    )
  );
}

function assertExecutionOptions(options: ExecuteVoucherDiscoveryOptions): void {
  const errors: string[] = [
    ...nonOperationalVoucherDiscoveryHarness.validate({
      fixtureManifestPath: options.fixtureManifestPath,
      manifest: options.manifest,
      approvedFixtureCompany: options.operatorCompany,
      productionCompanyNames: options.productionCompanyNames,
      dateFrom: options.manifest.dateRange.dateFrom,
      dateTo: options.manifest.dateRange.dateTo,
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      outputDirectory: options.outputDirectory,
      approvedOutputRoot: options.approvedOutputRoot,
      sanitizationMode: options.sanitizationMode,
      maximumResponseBytes: options.maximumResponseBytes,
      timeoutMs: options.timeoutMs,
      reviewerAcknowledgment: options.reviewerAcknowledgment,
      identityMutationExperimentRequested: options.experimentLabel !== 'baseline',
    }),
  ];
  const manifestValidation = validateVoucherDiscoveryManifest(options.manifest, {
    productionCompanyNames: options.productionCompanyNames,
  });
  errors.push(...manifestValidation.errors);
  errors.push(
    ...validateVoucherDiscoveryRequestShape({
      operationId: VOUCHER_DISCOVERY_OPERATION_ID,
      companyName: options.operatorCompany,
      ...options.manifest.dateRange,
    }),
  );
  if (!options.acknowledgeFixtureOnly) errors.push('Explicit fixture-only CLI acknowledgment is required.');
  if (
    !VOUCHER_DISCOVERY_EXPERIMENT_LABELS.includes(options.experimentLabel) ||
    (options.experimentLabel !== 'baseline' && options.manifest.approvedExperiments.length === 0)
  ) {
    errors.push('Experiment label is not authorized by the manifest.');
  }
  if (
    !Number.isInteger(options.maxXmlDepth) ||
    options.maxXmlDepth < 1 ||
    options.maxXmlDepth > APPROVED_XML_PARSER_MAX_DEPTH
  ) {
    errors.push('XML depth limit is invalid.');
  }
  if (
    !Number.isInteger(options.maxXmlNodeCount) ||
    options.maxXmlNodeCount < 1 ||
    options.maxXmlNodeCount > APPROVED_XML_PARSER_MAX_NODE_COUNT
  ) {
    errors.push('XML node-count limit is invalid.');
  }
  if (errors.length > 0) throw new Error(`Voucher discovery preflight failed: ${[...new Set(errors)].join(' ')}`);
  validateVoucherEvidenceLocations(options);
}

export async function executeVoucherDiscovery(
  options: ExecuteVoucherDiscoveryOptions,
  transport: ErpTransport,
): Promise<VoucherDiscoveryExecutionResult> {
  assertExecutionOptions(options);
  const request = buildVoucherDiscoveryRequest({
    operationId: VOUCHER_DISCOVERY_OPERATION_ID,
    companyName: options.operatorCompany,
    ...options.manifest.dateRange,
  });
  if (Buffer.byteLength(request.xml, 'utf8') > VOUCHER_DISCOVERY_MAX_REQUEST_BYTES) {
    throw new Error('Voucher discovery request exceeds its bounded request size.');
  }

  const memoryBefore = process.memoryUsage().heapUsed;
  const response = await transport.send({
    body: request.xml,
    contentType: 'text/xml; charset=utf-8',
    timeoutMs: options.timeoutMs,
    correlationId: `voucher-discovery-${options.experimentLabel}`,
  });
  const responseBytes = Buffer.byteLength(response.body, 'utf8');
  if (responseBytes > options.maximumResponseBytes) {
    throw new Error('Voucher discovery response exceeds the configured response-size limit.');
  }
  const parserByteLimit = Math.min(options.maximumResponseBytes, APPROVED_XML_PARSER_MAX_BYTES);
  const parseStarted = performance.now();
  const parser = new TallyXmlResponseParser();
  const document = parser.parse(response.body, {
    maxBytes: parserByteLimit,
    maxDepth: options.maxXmlDepth,
    maxNodeCount: options.maxXmlNodeCount,
  });
  const parseDurationMs = performance.now() - parseStarted;
  const memoryAfter = process.memoryUsage().heapUsed;

  const sourceErrorCount = parser.findAll(document, 'LINEERROR').length;
  const nodeCount = countParsedXmlNodes(document.root);
  const maxDepth = measureParsedXmlMaxDepth(document.root);
  const sanitized = sanitizeVoucherResponseStructure(document.root);
  const observedVoucherCount = sanitized.voucherRecordCount;
  const expectedCount = expectedVoucherCount(options.manifest);
  const expectedTypeSummary = expectedVoucherTypeSummary(options.manifest);
  const typeCountsMatch = voucherTypeSummariesMatch(
    expectedTypeSummary,
    sanitized.voucherTypeSummary,
  );
  const hasDuplicateIdentity = sanitized.identityEvidence.some(
    (item) => item.duplicateFingerprintCount > 0,
  );
  const envelopePresent = document.root.name === 'ENVELOPE';
  const countsMatch = observedVoucherCount === expectedCount;
  const resultState =
    envelopePresent &&
    sourceErrorCount === 0 &&
    countsMatch &&
    typeCountsMatch &&
    !hasDuplicateIdentity
      ? 'candidate-complete'
      : 'incomplete';
  const completedAt = new Date().toISOString();

  const evidenceFiles = writeVoucherDiscoveryEvidence(options, {
    requestShape: {
      operationId: request.operationId,
      companyToken: request.companyToken,
      dateFrom: request.dateFrom,
      dateTo: request.dateTo,
      requestBytes: Buffer.byteLength(request.xml, 'utf8'),
      requestKind: 'Collection',
      exportFormat: 'XML',
    },
    sanitizedResponseXml: sanitized.xml,
    fieldInventory: sanitized.fieldInventory,
    structuralCounts: {
      rootElement: document.root.name,
      nodeCount,
      maxDepth,
      observedVoucherCount,
      expectedVoucherCount: expectedCount,
      expectedVoucherTypeSummary: expectedTypeSummary,
      observedVoucherTypeSummary: sanitized.voucherTypeSummary,
      sourceErrorCount,
    },
    performance: {
      responseBytes,
      transportDurationMs: response.durationMs,
      parseDurationMs,
      heapUsedBeforeBytes: memoryBefore,
      heapUsedAfterBytes: memoryAfter,
      heapUsedDeltaBytes: memoryAfter - memoryBefore,
    },
    completenessMarkdown: [
      '# Voucher Discovery Completeness Observation',
      '',
      `- Transport success: yes`,
      `- Response inside configured limit: yes`,
      `- Parser success: yes`,
      `- Expected envelope present: ${envelopePresent ? 'yes' : 'no'}`,
      `- Source error/status lines: ${sourceErrorCount}`,
      `- Observed voucher count: ${observedVoucherCount}`,
      `- Manifest expected count: ${expectedCount}`,
      `- Counts reconciled: ${countsMatch ? 'yes' : 'no'}`,
      `- Voucher-type counts reconciled: ${typeCountsMatch ? 'yes' : 'no'}`,
      `- Distinct observed voucher types: ${sanitized.voucherTypeSummary.distinctVoucherTypeCount}`,
      `- Records missing voucher type: ${sanitized.voucherTypeSummary.unidentifiedVoucherTypeCount}`,
      `- Duplicate identity observation: ${hasDuplicateIdentity ? 'yes' : 'no'}`,
      `- Empty-result observation: ${observedVoucherCount === 0 ? 'yes' : 'no'}`,
      `- Timeout/cancellation: no`,
      `- Classification: ${resultState}`,
      '',
      'Reviewer approval is still required; parser success alone does not prove completeness.',
      '',
    ].join('\n'),
    privacyReview: {
      strictSanitizationApplied: true,
      rawXmlPersisted: false,
      businessTextPersisted: false,
      sourceIdentifiersInSanitizedArtifacts: false,
      identityEvidenceLocation: 'restricted-local-only',
    },
    runMetadata: {
      operationId: request.operationId,
      companyToken: request.companyToken,
      experimentLabel: options.experimentLabel,
      completedAt,
      technicalResultState: resultState,
    },
    identityEvidence: sanitized.identityEvidence,
  });
  return {
    resultState,
    responseBytes,
    durationMs: response.durationMs,
    observedVoucherCount,
    evidenceFiles,
  };
}
