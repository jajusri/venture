import type { ParsedXmlNode } from '../../tally/xml/response-parser.js';
import {
  isPlaceholderEntityNode,
  resolveRawEntityName,
} from '../../tally/contracts/master-data-envelope.js';

export interface MasterDataExtractionMetrics {
  readonly candidateNodeCount: number;
  readonly mappedRecordCount: number;
  readonly droppedRecordCount: number;
  readonly missingIdentityCount: number;
  readonly duplicateIdentityCount: number;
  readonly conflictingIdentityCount: number;
  readonly collectionPresent: boolean;
  readonly placeholderOnlyCollection: boolean;
}

export function computeMasterDataExtractionMetrics<T extends { id: string; guid?: string; normalizedName?: string }>(
  input: {
    readonly entityNodeName: string;
    readonly candidateNodes: readonly ParsedXmlNode[];
    readonly mappedBeforeDedupe: readonly T[];
    readonly mappedAfterDedupe: readonly T[];
    readonly collectionPresent: boolean;
  },
): MasterDataExtractionMetrics {
  const { entityNodeName, candidateNodes, mappedBeforeDedupe, mappedAfterDedupe, collectionPresent } =
    input;

  let missingIdentityCount = 0;
  let placeholderNodes = 0;
  for (const node of candidateNodes) {
    if (isPlaceholderEntityNode(node, entityNodeName)) {
      placeholderNodes += 1;
      continue;
    }
    const name = resolveRawEntityName(node);
    if (!name || name.toUpperCase() === entityNodeName.toUpperCase()) {
      missingIdentityCount += 1;
    }
  }

  const realCandidates = candidateNodes.filter(
    (node) => !isPlaceholderEntityNode(node, entityNodeName),
  );
  const mappedRecordCount = mappedAfterDedupe.length;
  const droppedRecordCount = Math.max(0, realCandidates.length - mappedBeforeDedupe.length);
  const duplicateIdentityCount = Math.max(0, mappedBeforeDedupe.length - mappedAfterDedupe.length);

  const guidCounts = new Map<string, number>();
  const nameToGuids = new Map<string, Set<string>>();
  for (const item of mappedBeforeDedupe) {
    const guid = item.guid?.trim();
    if (guid) {
      guidCounts.set(guid, (guidCounts.get(guid) ?? 0) + 1);
    }
    if (item.normalizedName) {
      const guids = nameToGuids.get(item.normalizedName) ?? new Set<string>();
      guids.add(guid ?? item.id);
      nameToGuids.set(item.normalizedName, guids);
    }
  }

  let conflictingIdentityCount = 0;
  for (const count of guidCounts.values()) {
    if (count > 1) conflictingIdentityCount += count - 1;
  }
  for (const guids of nameToGuids.values()) {
    if (guids.size > 1) conflictingIdentityCount += guids.size - 1;
  }

  const placeholderOnlyCollection =
    collectionPresent &&
    realCandidates.length === 0 &&
    candidateNodes.length > 0 &&
    placeholderNodes === candidateNodes.length;

  return {
    candidateNodeCount: realCandidates.length,
    mappedRecordCount,
    droppedRecordCount,
    missingIdentityCount,
    duplicateIdentityCount,
    conflictingIdentityCount,
    collectionPresent,
    placeholderOnlyCollection,
  };
}

export function toPrivacySafeExtractionMetrics(
  metrics: MasterDataExtractionMetrics,
): Record<string, number | boolean> {
  return {
    candidateNodeCount: metrics.candidateNodeCount,
    mappedRecordCount: metrics.mappedRecordCount,
    droppedRecordCount: metrics.droppedRecordCount,
    missingIdentityCount: metrics.missingIdentityCount,
    duplicateIdentityCount: metrics.duplicateIdentityCount,
    conflictingIdentityCount: metrics.conflictingIdentityCount,
    collectionPresent: metrics.collectionPresent,
    placeholderOnlyCollection: metrics.placeholderOnlyCollection,
  };
}
