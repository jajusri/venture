import { describe, expect, it } from 'vitest';

import type { ParsedGroupRecord } from '../../../src/tally/groups/groups-parser.js';
import { validateGroupHierarchy } from '../../../src/tally/groups/hierarchy-validator.js';

function record(id: string, name: string, parentName?: string): ParsedGroupRecord {
  return { id, name, parentName, sourceIndex: 0 };
}

describe('validateGroupHierarchy', () => {
  it('detects duplicate names with different ids', () => {
    const result = validateGroupHierarchy([
      record('id-a', 'Sales', 'Primary'),
      record('id-b', 'sales', 'Primary'),
    ]);
    expect(result.issues.some((i) => i.kind === 'DUPLICATE_NAME')).toBe(true);
    expect(result.hasBlockingIssues).toBe(true);
  });

  it('detects ambiguous parent when duplicate names exist', () => {
    const result = validateGroupHierarchy([
      record('a', 'Parent', 'Primary'),
      record('b', 'Parent', 'Primary'),
      record('c', 'Child', 'Parent'),
    ]);
    expect(result.issues.some((i) => i.kind === 'DUPLICATE_NAME')).toBe(true);
    expect(result.issues.some((i) => i.kind === 'AMBIGUOUS_PARENT')).toBe(true);
  });

  it('does not infinite-loop on deep chains', () => {
    const groups: ParsedGroupRecord[] = [];
    for (let i = 0; i < 200; i += 1) {
      groups.push(
        record(
          `node-${i}`,
          `Node ${i}`,
          i === 0 ? 'Primary' : `Node ${i - 1}`,
        ),
      );
    }
    const started = Date.now();
    const result = validateGroupHierarchy(groups);
    expect(Date.now() - started).toBeLessThan(500);
    expect(result.issues.filter((i) => i.kind === 'CYCLE')).toHaveLength(0);
  });

  it('detects cycles without unbounded recursion', () => {
    const result = validateGroupHierarchy([
      record('a', 'A', 'B'),
      record('b', 'B', 'A'),
    ]);
    expect(result.issues.some((i) => i.kind === 'CYCLE')).toBe(true);
  });

  it('treats Primary as virtual root for missing parent', () => {
    const result = validateGroupHierarchy([record('sales', 'Sales Accounts', 'Primary')]);
    expect(result.issues.filter((i) => i.kind === 'MISSING_PARENT')).toHaveLength(0);
  });
});
