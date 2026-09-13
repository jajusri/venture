import type { DataQuality } from '../../extraction/core/types.js';
import type {
  ErpGroupSummary,
  GroupExtractionStatus,
  HierarchyIssue,
} from '../../erp/ports/groups.js';
import { assessEnvelope } from './response-contract.js';
import type { GroupsParseResult } from '../groups/groups-parser.js';
import type { ParsedGroupRecord } from '../groups/groups-parser.js';
import {
  buildNameIndex,
  isPrimaryGroup,
  resolveParentStableId,
  validateGroupHierarchy,
} from '../groups/hierarchy-validator.js';

export const GROUPS_RESPONSE_CONTRACT_VERSION = '1';

export interface GroupsAssessment {
  readonly status: GroupExtractionStatus;
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
  readonly hierarchyIssues?: readonly HierarchyIssue[];
}

export function assessGroupsEnvelope(rawXml: string): DataQuality | undefined {
  return assessEnvelope(rawXml);
}

export function assessGroupsExtraction(
  parseResult: GroupsParseResult,
  groups: readonly ParsedGroupRecord[],
): GroupsAssessment {
  const hierarchy = validateGroupHierarchy(groups);
  const hierarchyIssues = [...hierarchy.issues];

  if (parseResult.duplicateNameConflicts > 0) {
    hierarchyIssues.push({
      kind: 'DUPLICATE_NAME',
      reason: `${parseResult.duplicateNameConflicts} duplicate name conflict(s) detected during parsing`,
    });
  }

  if (parseResult.duplicateIdConflicts > 0) {
    hierarchyIssues.push({
      kind: 'DUPLICATE_ID',
      reason: `${parseResult.duplicateIdConflicts} duplicate id conflict(s) detected during parsing`,
    });
  }

  if (groups.length > 0) {
    if (hierarchy.hasBlockingIssues || parseResult.duplicateIdConflicts > 0) {
      return {
        status: 'INCOMPLETE',
        dataQuality: {
          status: 'INCOMPLETE',
          reason: 'Groups recovered but hierarchy integrity checks failed',
        },
        reason: 'Groups recovered but hierarchy integrity checks failed',
        hierarchyIssues,
      };
    }

    if (
      parseResult.recordsMissingIdentity > 0 ||
      parseResult.skippedRecords > 0 ||
      hierarchyIssues.length > 0
    ) {
      return {
        status: 'SUCCESS',
        dataQuality: {
          status: 'INCOMPLETE',
          reason:
            `Recovered ${groups.length} groups with ${hierarchyIssues.length} hierarchy warning(s) ` +
            `and ${parseResult.recordsMissingIdentity} record(s) lacking identity`,
        },
        hierarchyIssues: hierarchyIssues.length > 0 ? hierarchyIssues : undefined,
      };
    }

    return { status: 'SUCCESS' };
  }

  if (parseResult.recordsMissingIdentity > 0) {
    return {
      status: 'INCOMPLETE',
      dataQuality: {
        status: 'INCOMPLETE',
        reason: 'Group nodes were present but none had a usable identity (NAME)',
      },
      reason: 'Group nodes were present but none had a usable identity (NAME)',
      hierarchyIssues,
    };
  }

  return {
    status: 'EMPTY',
    dataQuality: {
      status: 'EMPTY',
      reason: 'Tally returned a valid envelope with no discoverable groups',
    },
    reason: 'No accounting groups are currently available from Tally',
    hierarchyIssues: hierarchyIssues.length > 0 ? hierarchyIssues : undefined,
  };
}

export function mapParsedToErpSummary(groups: readonly ParsedGroupRecord[]): ErpGroupSummary[] {
  const idsByName = buildNameIndex(groups);
  return groups.map((group) => ({
    id: group.id,
    name: group.name,
    parentName: group.parentName,
    parentStableId: resolveParentStableId(group, idsByName),
    isPrimary: isPrimaryGroup(group),
    isRevenue: group.isRevenue,
    isDebit: group.isDebit,
    reservedName: group.reservedName,
  }));
}
