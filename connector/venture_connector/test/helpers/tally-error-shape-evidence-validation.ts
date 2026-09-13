import fs from 'node:fs';

import { assertEvidencePrivacySafe } from './tally-error-shape-scanner.js';

export const TALLY_ERROR_SHAPE_EVIDENCE_PATH = '../../docs/diagnostics/m5b-tally-error-shape-validation.json';

export function validateEvidenceFile(filePath: string): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  if (!fs.existsSync(filePath)) {
    return { valid: false, errors: ['Evidence file missing.'] };
  }
  const payload = JSON.parse(fs.readFileSync(filePath, 'utf8')) as Record<string, unknown>;
  for (const key of ['validatedAt', 'validationType', 'scenarios', 'privacyReview']) {
    if (!(key in payload)) errors.push(`Missing top-level field: ${key}`);
  }
  errors.push(...assertEvidencePrivacySafe(payload));
  const scenarios = payload.scenarios;
  if (!Array.isArray(scenarios) || scenarios.length === 0) {
    errors.push('scenarios must be a non-empty array.');
  }
  return { valid: errors.length === 0, errors };
}
