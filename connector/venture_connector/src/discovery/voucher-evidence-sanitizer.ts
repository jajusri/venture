import { createHash } from 'node:crypto';

import type { ParsedXmlNode } from '../tally/xml/response-parser.js';

const BOOLEAN_VALUES = new Set(['YES', 'NO', 'TRUE', 'FALSE']);
const IDENTITY_FIELDS = new Set(['GUID', 'MASTERID', 'ALTERID']);

export interface VoucherFieldInventoryEntry {
  readonly path: string;
  readonly fieldName: string;
  readonly occurrences: number;
  readonly textPresent: boolean;
  readonly childNames: readonly string[];
}

export interface VoucherIdentityEvidence {
  readonly fieldName: 'GUID' | 'MASTERID' | 'ALTERID';
  readonly fingerprints: readonly string[];
  readonly duplicateFingerprintCount: number;
}

export interface VoucherTypeCount {
  readonly identifierHash: string;
  readonly recordCount: number;
}

export interface PrivacySafeVoucherTypeSummary {
  readonly distinctVoucherTypeCount: number;
  readonly unidentifiedVoucherTypeCount: number;
  readonly perType: readonly VoucherTypeCount[];
}

export interface SanitizedVoucherStructure {
  readonly xml: string;
  readonly fieldInventory: readonly VoucherFieldInventoryEntry[];
  readonly identityEvidence: readonly VoucherIdentityEvidence[];
  readonly voucherRecordCount: number;
  readonly voucherTypeSummary: PrivacySafeVoucherTypeSummary;
}

function escapeXml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function sanitizeText(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase();
  return BOOLEAN_VALUES.has(normalized) ? normalized : '[REDACTED]';
}

function serializeSanitized(node: ParsedXmlNode, depth = 0): string {
  const indent = '  '.repeat(depth);
  const attributes = Object.keys(node.attributes)
    .sort()
    .map((key) => ` ${key}="[REDACTED]"`)
    .join('');
  const text = sanitizeText(node.text);
  if (node.children.length === 0) {
    return text
      ? `${indent}<${node.name}${attributes}>${escapeXml(text)}</${node.name}>`
      : `${indent}<${node.name}${attributes}/>`;
  }
  const children = node.children.map((child) => serializeSanitized(child, depth + 1)).join('\n');
  return `${indent}<${node.name}${attributes}>\n${children}\n${indent}</${node.name}>`;
}

function collectInventory(
  node: ParsedXmlNode,
  path: string,
  entries: Map<string, { count: number; textPresent: boolean; children: Set<string> }>,
): void {
  const nodePath = `${path}/${node.name}`;
  const current = entries.get(nodePath) ?? {
    count: 0,
    textPresent: false,
    children: new Set<string>(),
  };
  current.count += 1;
  current.textPresent ||= Boolean(node.text?.trim());
  node.children.forEach((child) => current.children.add(child.name));
  entries.set(nodePath, current);
  node.children.forEach((child) => collectInventory(child, nodePath, entries));
}

function collectIdentityValues(
  node: ParsedXmlNode,
  values: Map<'GUID' | 'MASTERID' | 'ALTERID', string[]>,
): void {
  if (IDENTITY_FIELDS.has(node.name) && node.text?.trim()) {
    values.get(node.name as 'GUID' | 'MASTERID' | 'ALTERID')!.push(node.text.trim());
  }
  node.children.forEach((child) => collectIdentityValues(child, values));
}

function identityFingerprint(fieldName: string, value: string): string {
  return createHash('sha256').update(`${fieldName}\0${value}`, 'utf8').digest('hex');
}

export function voucherTypeIdentifierHash(value: string): string {
  return createHash('sha256')
    .update(`VOUCHERTYPE\0${value.trim()}`, 'utf8')
    .digest('hex');
}

function childrenNamed(node: ParsedXmlNode | undefined, name: string): readonly ParsedXmlNode[] {
  if (!node) return [];
  return node.children.filter((child) => child.name === name);
}

export function findVoucherRecordNodes(root: ParsedXmlNode): readonly ParsedXmlNode[] {
  const body = childrenNamed(root, 'BODY')[0];
  const data = childrenNamed(body, 'DATA')[0];
  return childrenNamed(data, 'COLLECTION').flatMap((collection) =>
    childrenNamed(collection, 'VOUCHER'),
  );
}

function summarizeVoucherTypes(
  voucherRecords: readonly ParsedXmlNode[],
): PrivacySafeVoucherTypeSummary {
  const counts = new Map<string, number>();
  let unidentifiedVoucherTypeCount = 0;
  for (const voucher of voucherRecords) {
    const voucherType = childrenNamed(voucher, 'VOUCHERTYPENAME')[0]?.text?.trim();
    if (!voucherType) {
      unidentifiedVoucherTypeCount += 1;
      continue;
    }
    const identifierHash = voucherTypeIdentifierHash(voucherType);
    counts.set(identifierHash, (counts.get(identifierHash) ?? 0) + 1);
  }
  return {
    distinctVoucherTypeCount: counts.size,
    unidentifiedVoucherTypeCount,
    perType: [...counts.entries()]
      .map(([identifierHash, recordCount]) => ({ identifierHash, recordCount }))
      .sort((left, right) => left.identifierHash.localeCompare(right.identifierHash)),
  };
}

export function sanitizeVoucherResponseStructure(root: ParsedXmlNode): SanitizedVoucherStructure {
  const inventory = new Map<
    string,
    { count: number; textPresent: boolean; children: Set<string> }
  >();
  collectInventory(root, '', inventory);

  const identityValues = new Map<'GUID' | 'MASTERID' | 'ALTERID', string[]>([
    ['GUID', []],
    ['MASTERID', []],
    ['ALTERID', []],
  ]);
  const voucherRecords = findVoucherRecordNodes(root);
  voucherRecords.forEach((voucher) => collectIdentityValues(voucher, identityValues));

  return {
    xml: `${serializeSanitized(root)}\n`,
    fieldInventory: [...inventory.entries()]
      .map(([path, value]) => ({
        path,
        fieldName: path.slice(path.lastIndexOf('/') + 1),
        occurrences: value.count,
        textPresent: value.textPresent,
        childNames: [...value.children].sort(),
      }))
      .sort((left, right) => left.path.localeCompare(right.path)),
    identityEvidence: [...identityValues.entries()].map(([fieldName, rawValues]) => {
      const fingerprints = rawValues.map((value) => identityFingerprint(fieldName, value));
      return {
        fieldName,
        fingerprints,
        duplicateFingerprintCount: fingerprints.length - new Set(fingerprints).size,
      };
    }),
    voucherRecordCount: voucherRecords.length,
    voucherTypeSummary: summarizeVoucherTypes(voucherRecords),
  };
}
