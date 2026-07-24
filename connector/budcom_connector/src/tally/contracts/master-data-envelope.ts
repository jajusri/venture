import type { ParsedXmlDocument, ParsedXmlNode } from '../xml/response-parser.js';
import { isCountMetadata, normalizeText } from '../../extraction/normalization/strings.js';

/**
 * Shared structural helpers for rich ledger and stock-item master-data responses.
 * Expected path: ENVELOPE → BODY → DATA → COLLECTION → entity nodes.
 */

export function findChild(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
  const target = name.toUpperCase();
  return node.children.find((child) => child.name.toUpperCase() === target);
}

export function findFirstInTree(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
  const target = name.toUpperCase();
  if (node.name.toUpperCase() === target) {
    return node;
  }
  for (const child of node.children) {
    const match = findFirstInTree(child, target);
    if (match) return match;
  }
  return undefined;
}

/** True when Tally returned an explicit LINEERROR node (synthetic + committed fixture evidence). */
export function detectTallyLineError(rawXml: string, document?: ParsedXmlDocument): boolean {
  if (/<LINEERROR[\s>/]/i.test(rawXml)) {
    return true;
  }
  if (document) {
    return findFirstInTree(document.root, 'LINEERROR') !== undefined;
  }
  return false;
}

/**
 * Optional associated metadata when LINEERROR is present and HEADER/STATUS is zero.
 * Standalone STATUS values are never treated as proven Tally failures — live semantics unconfirmed.
 */
export function hasAssociatedHeaderStatusZero(rawXml: string, document?: ParsedXmlDocument): boolean {
  if (!detectTallyLineError(rawXml, document)) {
    return false;
  }
  if (/<HEADER>[\s\S]*?<STATUS>\s*0\s*<\/STATUS>[\s\S]*?<\/HEADER>/i.test(rawXml)) {
    return true;
  }
  return document ? readHeaderStatus(document) === '0' : false;
}

export function readHeaderStatus(document: ParsedXmlDocument): string | undefined {
  const envelopeRoot = document.root.name.toUpperCase() === 'ENVELOPE' ? document.root : document.root;
  const header = findChild(envelopeRoot, 'HEADER');
  if (!header) return undefined;
  const statusNode = findChild(header, 'STATUS');
  return normalizeText(statusNode?.text);
}

/** COLLECTION nodes directly under ENVELOPE/BODY/DATA. */
export function findBodyDataCollections(document: ParsedXmlDocument): readonly ParsedXmlNode[] {
  const envelopeRoot = document.root.name.toUpperCase() === 'ENVELOPE' ? document.root : document.root;
  const body = findChild(envelopeRoot, 'BODY');
  if (!body) return [];
  const data = findChild(body, 'DATA');
  if (!data) return [];
  return data.children.filter((child) => child.name.toUpperCase() === 'COLLECTION');
}

/** Direct child entity nodes inside requested DATA/COLLECTION subtrees only. */
export function collectDirectEntityNodes(
  collections: readonly ParsedXmlNode[],
  entityNodeName: string,
): ParsedXmlNode[] {
  const target = entityNodeName.toUpperCase();
  const nodes: ParsedXmlNode[] = [];
  for (const collection of collections) {
    for (const child of collection.children) {
      if (child.name.toUpperCase() === target) {
        nodes.push(child);
      }
    }
  }
  return nodes;
}

export function resolveRawEntityName(node: ParsedXmlNode): string | undefined {
  return (
    normalizeText(findChild(node, 'NAME')?.text) ??
    normalizeText(node.attributes.NAME) ??
    normalizeText(node.text)
  );
}

export function isPlaceholderEntityNode(node: ParsedXmlNode, entityNodeName: string): boolean {
  const name = resolveRawEntityName(node);
  if (!name) {
    return node.children.length === 0;
  }
  if (name.toUpperCase() === entityNodeName.toUpperCase()) {
    return true;
  }
  return isCountMetadata(name);
}

export function bodyDataRegionPresent(document: ParsedXmlDocument): boolean {
  const envelopeRoot = document.root.name.toUpperCase() === 'ENVELOPE' ? document.root : document.root;
  const body = findChild(envelopeRoot, 'BODY');
  if (!body) return false;
  return findChild(body, 'DATA') !== undefined;
}
