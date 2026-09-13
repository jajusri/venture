import { describe, expect, it } from 'vitest';

import {
  assessGroupsExtraction,
  assessGroupsEnvelope,
  mapParsedToErpSummary,
} from '../../../src/tally/contracts/groups-contract.js';
import { GroupsParser } from '../../../src/tally/groups/groups-parser.js';
import { TallyXmlResponseParser } from '../../../src/tally/xml/response-parser.js';
import {
  GROUP_CYCLE,
  GROUP_DEEP_HIERARCHY,
  GROUP_DUPLICATE_IDENTICAL,
  GROUP_EMPTY_LIST,
  GROUP_MISSING_NAME,
  GROUP_MISSING_PARENT,
  GROUP_MULTIPLE_HIERARCHY,
  GROUP_ONE_ROOT,
  GROUP_SELF_PARENT,
  GROUP_UNICODE,
  GROUP_WHITESPACE,
  buildLargeHierarchyXml,
} from '../../helpers/groups-fixtures.js';

describe('GroupsParser', () => {
  const xmlParser = new TallyXmlResponseParser();
  const parser = new GroupsParser(xmlParser);

  it('parses one valid root group', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_ONE_ROOT));
    expect(result.groups).toHaveLength(1);
    expect(result.groups[0]?.name).toBe('Capital Account');
    expect(result.groups[0]?.id).toBe('capital-account');
  });

  it('parses multiple root and child groups', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_MULTIPLE_HIERARCHY));
    expect(result.groups.length).toBeGreaterThanOrEqual(3);
    expect(result.groups.find((g) => g.name === 'Sundry Debtors')?.parentName).toBe('Current Assets');
  });

  it('parses deep hierarchy', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_DEEP_HIERARCHY));
    expect(result.groups).toHaveLength(4);
  });

  it('removes duplicate identical records', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_DUPLICATE_IDENTICAL));
    expect(result.groups).toHaveLength(1);
    expect(result.duplicateRecordsRemoved).toBe(1);
  });

  it('returns empty list for empty collection', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_EMPTY_LIST));
    expect(result.groups).toEqual([]);
  });

  it('counts records missing required name', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_MISSING_NAME));
    expect(result.groups).toEqual([]);
    expect(result.recordsMissingIdentity).toBeGreaterThan(0);
  });

  it('normalizes whitespace in names and parents', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_WHITESPACE));
    expect(result.groups[0]?.name).toBe('Sundry Debtors');
    expect(result.groups[0]?.parentName).toBe('Current Assets');
  });

  it('preserves unicode and special characters', () => {
    const result = parser.parseGroups(xmlParser.parse(GROUP_UNICODE));
    expect(result.groups[0]?.name).toBe('Müller & Söhne Gruppe');
    expect(result.groups[0]?.id).toBeTruthy();
  });

  it('handles large synthetic hierarchy efficiently', () => {
    const xml = buildLargeHierarchyXml(500);
    const started = Date.now();
    const result = parser.parseGroups(xmlParser.parse(xml));
    expect(result.groups).toHaveLength(500);
    expect(Date.now() - started).toBeLessThan(2000);
  });
});

describe('groups response contract', () => {
  const xmlParser = new TallyXmlResponseParser();
  const parser = new GroupsParser(xmlParser);

  it('flags unexpected response envelope as drift', () => {
    const issue = assessGroupsEnvelope('<RESPONSE/>');
    expect(issue?.status).toBe('DRIFT');
  });

  it('assesses SUCCESS for trusted group list', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_MULTIPLE_HIERARCHY));
    expect(assessGroupsExtraction(parseResult, parseResult.groups).status).toBe('SUCCESS');
  });

  it('assesses EMPTY for valid empty collection', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_EMPTY_LIST));
    const assessment = assessGroupsExtraction(parseResult, parseResult.groups);
    expect(assessment.status).toBe('EMPTY');
  });

  it('assesses INCOMPLETE when identity fields are missing', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_MISSING_NAME));
    expect(assessGroupsExtraction(parseResult, parseResult.groups).status).toBe('INCOMPLETE');
  });

  it('assesses INCOMPLETE for missing parent references', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_MISSING_PARENT));
    const assessment = assessGroupsExtraction(parseResult, parseResult.groups);
    expect(assessment.status).toBe('INCOMPLETE');
    expect(assessment.hierarchyIssues?.some((i) => i.kind === 'MISSING_PARENT')).toBe(true);
  });

  it('assesses INCOMPLETE for self-parenting', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_SELF_PARENT));
    const assessment = assessGroupsExtraction(parseResult, parseResult.groups);
    expect(assessment.status).toBe('INCOMPLETE');
    expect(assessment.hierarchyIssues?.some((i) => i.kind === 'SELF_PARENT')).toBe(true);
  });

  it('assesses INCOMPLETE for cyclic hierarchy', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_CYCLE));
    const assessment = assessGroupsExtraction(parseResult, parseResult.groups);
    expect(assessment.status).toBe('INCOMPLETE');
    expect(assessment.hierarchyIssues?.some((i) => i.kind === 'CYCLE')).toBe(true);
  });

  it('maps parentStableId and isPrimary in domain summaries', () => {
    const parseResult = parser.parseGroups(xmlParser.parse(GROUP_DEEP_HIERARCHY));
    const items = mapParsedToErpSummary(parseResult.groups);
    const level2 = items.find((g) => g.name === 'Level 2');
    expect(level2?.parentStableId).toBe('level-1');
    expect(level2?.isPrimary).toBe(false);
    const level1 = items.find((g) => g.name === 'Level 1');
    expect(level1?.isPrimary).toBe(true);
  });

  it('rejects malformed XML at parse time', () => {
    expect(() => xmlParser.parse('<ENVELOPE><BODY>')).toThrow('Invalid XML');
  });
});
