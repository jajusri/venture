import type { ParsedXmlNode, ParsedXmlDocument } from '../tally/xml/response-parser.js';
import {
  collectDirectEntityNodes,
  findBodyDataCollections,
} from '../tally/contracts/master-data-envelope.js';
import { ApprovedOperationId } from '../tally/registry/operation-registry.js';
import { resolveXmlParserMaxBytesForOperation } from '../tally/xml/response-parser-limits.js';
import { TallyXmlResponseParser } from '../tally/xml/response-parser.js';
import {
  InboundXmlResourceKind,
  type InboundXmlResourceKind as InboundXmlResourceKindType,
} from './inbound-xml-types.js';
import { InboundXmlReasonCode } from './inbound-xml-reason-codes.js';

const parser = new TallyXmlResponseParser();

const ENTITY_TO_RESOURCE: ReadonlyArray<{
  readonly nodeName: string;
  readonly resourceKind: InboundXmlResourceKindType;
}> = [
  { nodeName: 'LEDGER', resourceKind: InboundXmlResourceKind.Ledgers },
  { nodeName: 'STOCKITEM', resourceKind: InboundXmlResourceKind.StockItems },
  { nodeName: 'GROUP', resourceKind: InboundXmlResourceKind.LedgerGroups },
  { nodeName: 'COMPANY', resourceKind: InboundXmlResourceKind.CompanyList },
];

export function detectInboundXmlResourceKind(
  document: ParsedXmlDocument,
): InboundXmlResourceKindType {
  const collections = findBodyDataCollections(document);
  const counts = new Map<InboundXmlResourceKindType, number>();

  for (const { nodeName, resourceKind } of ENTITY_TO_RESOURCE) {
    const total = collections.length > 0
      ? collectDirectEntityNodes(collections, nodeName).length
      : parser.findAll(document, nodeName).length;
    if (total > 0) {
      counts.set(resourceKind, total);
    }
  }

  const detected = [...counts.entries()];
  if (detected.length === 0) {
    throw resourceError(
      InboundXmlReasonCode.UnknownResourceKind,
      'Inbound XML does not match an approved resource kind.',
    );
  }
  if (detected.length > 1) {
    throw resourceError(
      InboundXmlReasonCode.UnknownResourceKind,
      'Inbound XML contains ambiguous resource kinds.',
    );
  }

  return detected[0]![0];
}

export function assertExpectedResourceKind(
  detected: InboundXmlResourceKindType,
  expected: InboundXmlResourceKindType | undefined,
): void {
  if (expected && detected !== expected) {
    throw resourceError(
      InboundXmlReasonCode.ResourceKindMismatch,
      'Inbound XML resource kind does not match the expected kind.',
    );
  }
}

export function extractSourceCompanyName(rawXml: string): string | undefined {
  const matches = [...rawXml.matchAll(/<SVCURRENTCOMPANY>([^<]*)<\/SVCURRENTCOMPANY>/gi)];
  const values = matches
    .map((match) => match[1]?.trim())
    .filter((value): value is string => Boolean(value));
  const unique = [...new Set(values.map((value) => value.toLowerCase()))];
  if (unique.length === 0) {
    return undefined;
  }
  if (unique.length > 1) {
    throw resourceError(
      InboundXmlReasonCode.AmbiguousCompanyIdentity,
      'Inbound XML contains ambiguous company identity markers.',
    );
  }
  return values[0];
}

export function assertCompanyIdentity(options: {
  readonly rawXml: string;
  readonly resourceKind: InboundXmlResourceKindType;
  readonly targetCompanyId?: string;
  readonly targetCompanyName?: string;
}): string | undefined {
  const sourceCompany = extractSourceCompanyName(options.rawXml);
  const requiresCompany =
    options.resourceKind === InboundXmlResourceKind.Ledgers
    || options.resourceKind === InboundXmlResourceKind.StockItems
    || options.resourceKind === InboundXmlResourceKind.LedgerGroups;

  if (requiresCompany && !sourceCompany && !options.targetCompanyName) {
    throw resourceError(
      InboundXmlReasonCode.MissingCompanyIdentity,
      'Inbound XML is missing required company identity.',
    );
  }

  if (options.targetCompanyName && sourceCompany) {
    if (options.targetCompanyName.trim().toLowerCase() !== sourceCompany.trim().toLowerCase()) {
      throw resourceError(
        InboundXmlReasonCode.CompanyMismatch,
        'Inbound XML company identity does not match the selected company.',
      );
    }
  }

  return sourceCompany;
}

function resourceError(reasonCode: InboundXmlReasonCode, message: string): Error {
  const error = new Error(message);
  (error as Error & { reasonCode: InboundXmlReasonCode }).reasonCode = reasonCode;
  return error;
}

export function resolveParserMaxBytes(resourceKind: InboundXmlResourceKindType): number {
  switch (resourceKind) {
    case InboundXmlResourceKind.Ledgers:
      return resolveXmlParserMaxBytesForOperation(ApprovedOperationId.Ledgers);
    case InboundXmlResourceKind.StockItems:
      return resolveXmlParserMaxBytesForOperation(ApprovedOperationId.StockItems);
    case InboundXmlResourceKind.LedgerGroups:
      return resolveXmlParserMaxBytesForOperation(ApprovedOperationId.LedgerGroups);
    case InboundXmlResourceKind.CompanyList:
      return resolveXmlParserMaxBytesForOperation(ApprovedOperationId.CompanyList);
    default:
      return resolveXmlParserMaxBytesForOperation(ApprovedOperationId.CompanyList);
  }
}

export function countParsedNodes(root: ParsedXmlNode): number {
  return 1 + root.children.reduce((sum, child) => sum + countNode(child), 0);
}

function countNode(node: ParsedXmlNode): number {
  return 1 + node.children.reduce((sum, child) => sum + countNode(child), 0);
}
