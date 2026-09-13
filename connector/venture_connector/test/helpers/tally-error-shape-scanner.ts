import {
  assessLedgerMasterDataEnvelope,
  assessLedgerMasterDataStructure,
  toPrivacySafeLedgerContractAssessment,
} from '../../src/tally/contracts/ledger-master-data-contract.js';
import {
  assessStockItemMasterDataEnvelope,
  assessStockItemMasterDataStructure,
  toPrivacySafeStockItemContractAssessment,
} from '../../src/tally/contracts/stock-item-master-data-contract.js';
import {
  bodyDataRegionPresent,
  detectTallyLineError,
  findBodyDataCollections,
  findChild,
  findFirstInTree,
  hasAssociatedHeaderStatusZero,
  readHeaderStatus,
} from '../../src/tally/contracts/master-data-envelope.js';
import {
  TallyXmlResponseParser,
  XmlParseError,
  type ParsedXmlDocument,
} from '../../src/tally/xml/response-parser.js';

export type ErrorShapeContractKind = 'ledger' | 'stock' | 'none';

export type ParserOutcome = 'accepted' | 'rejected' | 'not_applicable';

export interface CollectionMetadataSummary {
  readonly collectionCount: number;
  readonly collectionAttributeKeySets: readonly (readonly string[])[];
  readonly hasCollectionNameAttribute: boolean;
  readonly hasCollectionTypeAttribute: boolean;
  readonly directChildElementCounts: Readonly<Record<string, number>>;
}

export interface ErrorShapeScanInput {
  readonly scenarioId: string;
  readonly requestKind: string;
  readonly transportStatus: number | null;
  readonly responseByteLength: number;
  readonly rawXml?: string;
  readonly durationMs: number;
  readonly correlationId: string;
  readonly contractKind: ErrorShapeContractKind;
  readonly transportErrorCode?: string;
  readonly transportErrorMessage?: string;
}

export interface ErrorShapeScanResult {
  readonly scenarioId: string;
  readonly correlationId: string;
  readonly requestKind: string;
  readonly transportStatus: number | null;
  readonly responseByteLength: number;
  readonly durationMs: number;
  readonly rootElement?: string;
  readonly envelopePresent: boolean;
  readonly headerPresent: boolean;
  readonly headerStatusPresent: boolean;
  readonly headerStatusClassifiedValue?: '0' | '1';
  readonly lineErrorPresent: boolean;
  readonly lineErrorLength?: number;
  readonly associatedHeaderStatusZero: boolean;
  readonly bodyPresent: boolean;
  readonly dataPresent: boolean;
  readonly collectionPresent: boolean;
  readonly collectionCount: number;
  readonly requestedEntityNodeCount: number;
  readonly unexpectedEntityNodeCount: number;
  readonly collectionMetadata?: CollectionMetadataSummary;
  readonly parserOutcome: ParserOutcome;
  readonly parserReason?: string;
  readonly contractStatus?: string;
  readonly contractReason?: string;
  readonly contractReasonCode?: string;
  readonly contractBlocking?: boolean;
  readonly transportErrorCode?: string;
  readonly transportErrorMessage?: string;
  readonly overallPass: boolean;
  readonly classificationNotes?: string;
}

const APPROVED_ENTITY_MARKERS = new Set([
  'LEDGER',
  'STOCKITEM',
  'GROUP',
  'STOCKGROUP',
  'COMPANY',
  'LINEERROR',
  'COLLECTION',
  'DATA',
  'BODY',
  'HEADER',
  'ENVELOPE',
  'DESC',
  'STATICVARIABLES',
  'TDL',
  'TDLMESSAGE',
  'REMOTEINFO',
  'REMOTELINK',
]);

function classifyHeaderStatus(value: string | undefined): '0' | '1' | undefined {
  const normalized = value?.trim();
  if (normalized === '0' || normalized === '1') {
    return normalized;
  }
  return undefined;
}

function measureLineErrorLength(document: ParsedXmlDocument): number | undefined {
  const lineError = findFirstInTree(document.root, 'LINEERROR');
  if (!lineError) return undefined;
  return (lineError.text ?? '').trim().length;
}

function summarizeCollectionMetadata(document: ParsedXmlDocument): CollectionMetadataSummary {
  const collections = findBodyDataCollections(document);
  const attributeKeySets: string[][] = [];
  let hasCollectionNameAttribute = false;
  let hasCollectionTypeAttribute = false;
  const directChildElementCounts: Record<string, number> = {};

  for (const collection of collections) {
    const keys = Object.keys(collection.attributes).map((key) => key.toUpperCase());
    attributeKeySets.push(keys);
    if (keys.includes('NAME')) hasCollectionNameAttribute = true;
    if (keys.includes('TYPE')) hasCollectionTypeAttribute = true;
    for (const child of collection.children) {
      const marker = child.name.toUpperCase();
      if (APPROVED_ENTITY_MARKERS.has(marker)) {
        directChildElementCounts[marker] = (directChildElementCounts[marker] ?? 0) + 1;
      }
    }
  }

  return {
    collectionCount: collections.length,
    collectionAttributeKeySets: attributeKeySets,
    hasCollectionNameAttribute,
    hasCollectionTypeAttribute,
    directChildElementCounts,
  };
}

function countEntityNodes(
  document: ParsedXmlDocument,
  requestedEntity: string,
): { requestedEntityNodeCount: number; unexpectedEntityNodeCount: number } {
  const collections = findBodyDataCollections(document);
  const requested = requestedEntity.toUpperCase();
  let requestedEntityNodeCount = 0;
  let unexpectedEntityNodeCount = 0;

  for (const collection of collections) {
    for (const child of collection.children) {
      const marker = child.name.toUpperCase();
      if (!APPROVED_ENTITY_MARKERS.has(marker)) continue;
      if (marker === requested) {
        requestedEntityNodeCount += 1;
      } else if (marker !== 'COLLECTION') {
        unexpectedEntityNodeCount += 1;
      }
    }
  }

  return { requestedEntityNodeCount, unexpectedEntityNodeCount };
}

function assessContract(
  contractKind: ErrorShapeContractKind,
  rawXml: string,
  document: ParsedXmlDocument,
): Pick<
  ErrorShapeScanResult,
  'contractStatus' | 'contractReason' | 'contractReasonCode' | 'contractBlocking'
> {
  if (contractKind === 'none') {
    return {};
  }

  if (contractKind === 'ledger') {
    const envelope = assessLedgerMasterDataEnvelope(rawXml);
    const assessment = envelope ?? assessLedgerMasterDataStructure(document);
    const safe = toPrivacySafeLedgerContractAssessment(assessment, {
      associatedHeaderStatusZero: hasAssociatedHeaderStatusZero(rawXml, document),
    });
    return {
      contractStatus: String(safe.contractStatus),
      contractReasonCode: safe.reasonCode ? String(safe.reasonCode) : undefined,
      contractBlocking: safe.blocking === true,
    };
  }

  const envelope = assessStockItemMasterDataEnvelope(rawXml);
  const assessment = envelope ?? assessStockItemMasterDataStructure(document);
  const safe = toPrivacySafeStockItemContractAssessment(assessment, {
    associatedHeaderStatusZero: hasAssociatedHeaderStatusZero(rawXml, document),
  });
  return {
    contractStatus: String(safe.contractStatus),
    contractReasonCode: safe.reasonCode ? String(safe.reasonCode) : undefined,
    contractBlocking: safe.blocking === true,
  };
}

export function scanTallyErrorShapeResponse(input: ErrorShapeScanInput): ErrorShapeScanResult {
  const base: ErrorShapeScanResult = {
    scenarioId: input.scenarioId,
    correlationId: input.correlationId,
    requestKind: input.requestKind,
    transportStatus: input.transportStatus,
    responseByteLength: input.responseByteLength,
    durationMs: input.durationMs,
    envelopePresent: false,
    headerPresent: false,
    headerStatusPresent: false,
    lineErrorPresent: false,
    associatedHeaderStatusZero: false,
    bodyPresent: false,
    dataPresent: false,
    collectionPresent: false,
    collectionCount: 0,
    requestedEntityNodeCount: 0,
    unexpectedEntityNodeCount: 0,
    parserOutcome: input.rawXml ? 'accepted' : 'not_applicable',
    transportErrorCode: input.transportErrorCode,
    transportErrorMessage: input.transportErrorMessage,
    overallPass: false,
  };

  if (!input.rawXml?.trim()) {
    return {
      ...base,
      parserOutcome: 'not_applicable',
      overallPass: input.scenarioId === 'E6',
      classificationNotes:
        input.scenarioId === 'E6'
          ? 'Transport failure before XML body; contract classification not applicable.'
          : 'No XML body received.',
    };
  }

  const rawXml = input.rawXml;
  let document: ParsedXmlDocument | undefined;
  const parser = new TallyXmlResponseParser();

  try {
    document = parser.parse(rawXml);
  } catch (error) {
    const reason = error instanceof XmlParseError ? error.reason : 'xml_malformed';
    return {
      ...base,
      lineErrorPresent: detectTallyLineError(rawXml),
      parserOutcome: 'rejected',
      parserReason: reason,
      overallPass: false,
      classificationNotes: 'Parser rejected response before contract assessment.',
    };
  }

  const rootElement = document.root.name.toUpperCase();
  const envelopePresent = rootElement === 'ENVELOPE';
  const header = envelopePresent ? findChild(document.root, 'HEADER') : undefined;
  const headerStatus = readHeaderStatus(document);
  const headerStatusClassified = classifyHeaderStatus(headerStatus);
  const collections = findBodyDataCollections(document);
  const requestedEntity = input.contractKind === 'stock' ? 'STOCKITEM' : 'LEDGER';
  const entityCounts =
    input.contractKind === 'none'
      ? { requestedEntityNodeCount: 0, unexpectedEntityNodeCount: 0 }
      : countEntityNodes(document, requestedEntity);

  const contract = assessContract(input.contractKind, rawXml, document);

  return {
    ...base,
    rootElement,
    envelopePresent,
    headerPresent: header !== undefined,
    headerStatusPresent: headerStatus !== undefined,
    headerStatusClassifiedValue: headerStatusClassified,
    lineErrorPresent: detectTallyLineError(rawXml, document),
    lineErrorLength: measureLineErrorLength(document),
    associatedHeaderStatusZero: hasAssociatedHeaderStatusZero(rawXml, document),
    bodyPresent: envelopePresent && findChild(document.root, 'BODY') !== undefined,
    dataPresent: bodyDataRegionPresent(document),
    collectionPresent: collections.length > 0,
    collectionCount: collections.length,
    requestedEntityNodeCount: entityCounts.requestedEntityNodeCount,
    unexpectedEntityNodeCount: entityCounts.unexpectedEntityNodeCount,
    collectionMetadata: summarizeCollectionMetadata(document),
    parserOutcome: 'accepted',
    ...contract,
    overallPass: true,
  };
}

export function evaluateScenarioExpectation(
  result: ErrorShapeScanResult,
  expectation: {
    readonly expectParserAccepted?: boolean;
    readonly expectLineError?: boolean;
    readonly expectContractStatus?: string;
    readonly expectTransportFailure?: boolean;
    readonly expectCollectionPresent?: boolean;
  },
): ErrorShapeScanResult {
  let overallPass = result.overallPass;
  const notes: string[] = [];

  if (expectation.expectTransportFailure) {
    overallPass = result.transportStatus === null && !result.responseByteLength;
    if (!overallPass) notes.push('Expected transport failure without XML.');
  }

  if (expectation.expectParserAccepted !== undefined) {
    const parserOk =
      expectation.expectParserAccepted ? result.parserOutcome === 'accepted' : result.parserOutcome !== 'accepted';
    overallPass = overallPass && parserOk;
    if (!parserOk) notes.push('Parser outcome differed from expectation.');
  }

  if (expectation.expectLineError !== undefined) {
    overallPass = overallPass && result.lineErrorPresent === expectation.expectLineError;
    if (result.lineErrorPresent !== expectation.expectLineError) {
      notes.push('LINEERROR presence differed from expectation.');
    }
  }

  if (expectation.expectContractStatus !== undefined) {
    overallPass = overallPass && result.contractStatus === expectation.expectContractStatus;
    if (result.contractStatus !== expectation.expectContractStatus) {
      notes.push(`Contract status ${result.contractStatus ?? 'none'} != ${expectation.expectContractStatus}.`);
    }
  }

  if (expectation.expectCollectionPresent !== undefined) {
    overallPass = overallPass && result.collectionPresent === expectation.expectCollectionPresent;
  }

  return {
    ...result,
    overallPass,
    classificationNotes: notes.length > 0 ? notes.join(' ') : result.classificationNotes,
  };
}

export function compareRepeatRuns(
  first: ErrorShapeScanResult,
  second: ErrorShapeScanResult,
): {
  readonly stable: boolean;
  readonly differences: readonly string[];
} {
  const keys: Array<keyof ErrorShapeScanResult> = [
    'rootElement',
    'envelopePresent',
    'headerPresent',
    'headerStatusPresent',
    'headerStatusClassifiedValue',
    'lineErrorPresent',
    'bodyPresent',
    'dataPresent',
    'collectionPresent',
    'collectionCount',
    'parserOutcome',
    'contractStatus',
    'contractReasonCode',
  ];
  const differences: string[] = [];
  for (const key of keys) {
    if (first[key] !== second[key]) {
      differences.push(`${key}: ${String(first[key])} -> ${String(second[key])}`);
    }
  }

  const sizeDelta = Math.abs(first.responseByteLength - second.responseByteLength);
  const sizeStable = sizeDelta <= Math.max(64, first.responseByteLength * 0.05);
  if (!sizeStable) {
    differences.push(
      `responseByteLength delta ${sizeDelta} exceeds tolerance (${first.responseByteLength}/${second.responseByteLength})`,
    );
  }

  return { stable: differences.length === 0, differences };
}

export function assertEvidencePrivacySafe(payload: unknown): readonly string[] {
  const serialized = JSON.stringify(payload);
  const violations: string[] = [];
  const forbiddenPatterns = [
    /<LEDGER\b/i,
    /<STOCKITEM\b/i,
    /SVCURRENTCOMPANY/i,
    /GSTREGISTRATION/i,
    /OPENINGBALANCE/i,
    /CLOSINGBALANCE/i,
    /guid:/i,
    /[0-9]{15}/,
  ];
  for (const pattern of forbiddenPatterns) {
    if (pattern.test(serialized)) {
      violations.push(`Forbidden pattern matched: ${pattern.source}`);
    }
  }
  return violations;
}
