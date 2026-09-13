import type { ParsedXmlDocument, ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import { isCountMetadata, normalizeText, slugify } from '../../extraction/normalization/strings.js';

/** Tally virtual root group — may not appear in collection exports. */
export const TALLY_VIRTUAL_ROOT_NAME = 'Primary';

export interface ParsedGroupRecord {
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly isRevenue?: boolean;
  readonly isDebit?: boolean;
  readonly reservedName?: string;
  readonly sourceIndex: number;
}

export interface GroupsParseResult {
  readonly groups: readonly ParsedGroupRecord[];
  readonly skippedRecords: number;
  readonly duplicateRecordsRemoved: number;
  readonly recordsMissingIdentity: number;
  readonly duplicateIdConflicts: number;
  readonly duplicateNameConflicts: number;
}

/**
 * Parses accounting GROUP nodes from a Tally "List of Groups" response.
 */
export class GroupsParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parseGroups(document: ParsedXmlDocument): GroupsParseResult {
    const candidates: ParsedGroupRecord[] = [];
    let skippedRecords = 0;
    let recordsMissingIdentity = 0;

    const groupNodes = this.parser.findAll(document, 'GROUP');
    for (const node of groupNodes) {
      const mapped = this.mapGroupNode(node);
      if (!mapped) {
        if (this.isMetadataCountNode(node)) {
          skippedRecords += 1;
        } else if (this.looksLikeGroupNode(node)) {
          recordsMissingIdentity += 1;
        } else {
          skippedRecords += 1;
        }
        continue;
      }
      candidates.push(mapped);
    }

    if (candidates.length === 0) {
      const collectionNodes = this.parser.findAll(document, 'COLLECTION');
      for (const collection of collectionNodes) {
        for (const child of collection.children) {
          if (child.name.toUpperCase() !== 'GROUP') continue;
          const mapped = this.mapGroupNode(child);
          if (!mapped) {
            recordsMissingIdentity += 1;
            continue;
          }
          candidates.push(mapped);
        }
      }
    }

    const { groups, duplicateRecordsRemoved, duplicateIdConflicts, duplicateNameConflicts } =
      dedupeAndDetectConflicts(candidates);

    return {
      groups,
      skippedRecords,
      duplicateRecordsRemoved,
      recordsMissingIdentity,
      duplicateIdConflicts,
      duplicateNameConflicts,
    };
  }

  private mapGroupNode(node: ParsedXmlNode, sourceIndex = 0): ParsedGroupRecord | undefined {
    const name = this.resolveRawName(node);
    if (!name || name.toUpperCase() === 'GROUP' || isCountMetadata(name)) {
      return undefined;
    }

    const parentName = normalizeText(this.parser.getText(this.findChild(node, 'PARENT')));
    const isBuiltin = this.getLogical(node, 'ISBUILTIN');
    const reservedName =
      isBuiltin === true || normalizeNameKey(name) === normalizeNameKey(TALLY_VIRTUAL_ROOT_NAME)
        ? name
        : undefined;

    return {
      id: slugify(name),
      name,
      parentName,
      isRevenue: this.getLogical(node, 'ISREVENUE'),
      isDebit: this.getLogical(node, 'ISDEEMEDPOSITIVE'),
      reservedName,
      sourceIndex,
    };
  }

  private resolveRawName(node: ParsedXmlNode): string | undefined {
    return (
      normalizeText(this.parser.getText(this.findChild(node, 'NAME'))) ??
      normalizeText(node.attributes.NAME) ??
      normalizeText(node.text)
    );
  }

  private getLogical(node: ParsedXmlNode, childName: string): boolean | undefined {
    const value = normalizeText(this.parser.getText(this.findChild(node, childName)))?.toLowerCase();
    if (!value) return undefined;
    return value === 'yes' || value === 'true' || value === '1';
  }

  private isMetadataCountNode(node: ParsedXmlNode): boolean {
    const name = this.resolveRawName(node);
    return name !== undefined && isCountMetadata(name);
  }

  private looksLikeGroupNode(node: ParsedXmlNode): boolean {
    return (
      node.name.toUpperCase() === 'GROUP' ||
      node.attributes.NAME !== undefined ||
      this.findChild(node, 'NAME') !== undefined
    );
  }

  private findChild(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
    const target = name.toUpperCase();
    return node.children.find((child) => child.name.toUpperCase() === target);
  }
}

function normalizeNameKey(value: string): string {
  return value.normalize('NFC').trim().toLowerCase();
}

function dedupeAndDetectConflicts(records: ParsedGroupRecord[]): {
  groups: ParsedGroupRecord[];
  duplicateRecordsRemoved: number;
  duplicateIdConflicts: number;
  duplicateNameConflicts: number;
} {
  const seenIds = new Set<string>();
  const nameToIds = new Map<string, Set<string>>();
  const result: ParsedGroupRecord[] = [];
  let duplicateRecordsRemoved = 0;
  let duplicateIdConflicts = 0;

  for (const record of records) {
    const nameKey = normalizeNameKey(record.name);
    const idsForName = nameToIds.get(nameKey) ?? new Set<string>();
    idsForName.add(record.id);
    nameToIds.set(nameKey, idsForName);

    if (seenIds.has(record.id)) {
      duplicateRecordsRemoved += 1;
      duplicateIdConflicts += 1;
      continue;
    }
    seenIds.add(record.id);
    result.push(record);
  }

  let duplicateNameConflicts = 0;
  for (const ids of nameToIds.values()) {
    if (ids.size > 1) duplicateNameConflicts += ids.size - 1;
  }

  return {
    groups: result,
    duplicateRecordsRemoved,
    duplicateIdConflicts,
    duplicateNameConflicts,
  };
}

export { normalizeNameKey };
