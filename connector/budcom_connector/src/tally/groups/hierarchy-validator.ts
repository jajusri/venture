import type { HierarchyIssue } from '../../erp/ports/groups.js';
import type { ParsedGroupRecord } from './groups-parser.js';
import { TALLY_VIRTUAL_ROOT_NAME, normalizeNameKey } from './groups-parser.js';

export interface HierarchyValidationResult {
  readonly issues: readonly HierarchyIssue[];
  readonly hasBlockingIssues: boolean;
}

/**
 * Validates group hierarchy integrity without recursive traversal risk.
 * Uses indexed lookups and bounded parent-chain walks.
 */
export function validateGroupHierarchy(groups: readonly ParsedGroupRecord[]): HierarchyValidationResult {
  const issues: HierarchyIssue[] = [];
  const byId = new Map<string, ParsedGroupRecord>();
  const idsByName = new Map<string, string[]>();

  for (const group of groups) {
    if (!group.name.trim()) {
      issues.push({
        kind: 'EMPTY_NAME',
        groupId: group.id,
        reason: 'Group record has an empty name',
      });
      continue;
    }

    if (byId.has(group.id)) {
      issues.push({
        kind: 'DUPLICATE_ID',
        groupId: group.id,
        groupName: group.name,
        reason: `Duplicate stable id "${group.id}" for group "${group.name}"`,
      });
    } else {
      byId.set(group.id, group);
    }

    const nameKey = normalizeNameKey(group.name);
    const existing = idsByName.get(nameKey) ?? [];
    if (!existing.includes(group.id)) {
      existing.push(group.id);
      idsByName.set(nameKey, existing);
    }
  }

  for (const [nameKey, ids] of idsByName) {
    if (ids.length > 1) {
      issues.push({
        kind: 'DUPLICATE_NAME',
        reason: `Name "${nameKey}" maps to ${ids.length} different group ids: ${ids.join(', ')}`,
      });
    }
  }

  for (const group of groups) {
    const parentName = group.parentName?.trim();
    if (!parentName) continue;

    if (normalizeNameKey(parentName) === normalizeNameKey(group.name)) {
      issues.push({
        kind: 'SELF_PARENT',
        groupId: group.id,
        groupName: group.name,
        parentName,
        reason: `Group "${group.name}" references itself as parent`,
      });
      continue;
    }

    if (isVirtualRoot(parentName)) {
      continue;
    }

    const parentIds = idsByName.get(normalizeNameKey(parentName));
    if (!parentIds || parentIds.length === 0) {
      issues.push({
        kind: 'MISSING_PARENT',
        groupId: group.id,
        groupName: group.name,
        parentName,
        reason: `Parent "${parentName}" was not found among returned groups`,
      });
    } else if (parentIds.length > 1) {
      issues.push({
        kind: 'AMBIGUOUS_PARENT',
        groupId: group.id,
        groupName: group.name,
        parentName,
        reason: `Parent name "${parentName}" is ambiguous (${parentIds.length} matching ids)`,
      });
    }
  }

  const cycleIssues = detectCycles(groups, idsByName);
  issues.push(...cycleIssues);

  const hasBlockingIssues = issues.some((issue) =>
    [
      'CYCLE',
      'SELF_PARENT',
      'DUPLICATE_ID',
      'DUPLICATE_NAME',
      'EMPTY_NAME',
      'MISSING_PARENT',
      'AMBIGUOUS_PARENT',
    ].includes(issue.kind),
  );

  return { issues, hasBlockingIssues };
}

function isVirtualRoot(parentName: string): boolean {
  return normalizeNameKey(parentName) === normalizeNameKey(TALLY_VIRTUAL_ROOT_NAME);
}

function detectCycles(
  groups: readonly ParsedGroupRecord[],
  idsByName: Map<string, string[]>,
): HierarchyIssue[] {
  const issues: HierarchyIssue[] = [];
  const parentIdByGroupId = new Map<string, string | undefined>();

  for (const group of groups) {
    parentIdByGroupId.set(group.id, resolveParentId(group, idsByName));
  }

  for (const group of groups) {
    const visited = new Set<string>();
    let current: string | undefined = group.id;
    let steps = 0;
    const maxSteps = groups.length + 1;

    while (current && steps <= maxSteps) {
      if (visited.has(current)) {
        issues.push({
          kind: 'CYCLE',
          groupId: group.id,
          groupName: group.name,
          reason: `Hierarchy cycle detected involving group "${group.name}"`,
        });
        break;
      }
      visited.add(current);
      current = parentIdByGroupId.get(current);
      steps += 1;
    }

    if (steps > maxSteps) {
      issues.push({
        kind: 'CYCLE',
        groupId: group.id,
        groupName: group.name,
        reason: `Hierarchy depth exceeded safe limit for group "${group.name}"`,
      });
    }
  }

  return issues;
}

function resolveParentId(
  group: ParsedGroupRecord,
  idsByName: Map<string, string[]>,
): string | undefined {
  const parentName = group.parentName?.trim();
  if (!parentName || isVirtualRoot(parentName)) return undefined;
  const ids = idsByName.get(normalizeNameKey(parentName));
  if (!ids || ids.length !== 1) return undefined;
  return ids[0];
}

export function resolveParentStableId(
  group: ParsedGroupRecord,
  idsByName: Map<string, string[]>,
): string | undefined {
  return resolveParentId(group, idsByName);
}

export function isPrimaryGroup(group: ParsedGroupRecord): boolean {
  const parent = group.parentName?.trim();
  if (!parent) return true;
  return isVirtualRoot(parent) || normalizeNameKey(group.name) === normalizeNameKey(TALLY_VIRTUAL_ROOT_NAME);
}

export function buildNameIndex(groups: readonly ParsedGroupRecord[]): Map<string, string[]> {
  const idsByName = new Map<string, string[]>();
  for (const group of groups) {
    const key = normalizeNameKey(group.name);
    const ids = idsByName.get(key) ?? [];
    if (!ids.includes(group.id)) ids.push(group.id);
    idsByName.set(key, ids);
  }
  return idsByName;
}
