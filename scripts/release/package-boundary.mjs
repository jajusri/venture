#!/usr/bin/env node
/**
 * Budcom controlled-pilot packaging boundary inspector.
 * Scans artifact directories and fails closed on forbidden content.
 */
import fs from 'node:fs';
import path from 'node:path';

export const FORBIDDEN_PATTERNS = [
  { id: 'env-file', pattern: /^\.env(\..+)?$/i, reason: 'Environment file' },
  { id: 'database', pattern: /\.(db|sqlite|sqlite3)$/i, reason: 'Database file' },
  { id: 'log-file', pattern: /\.log$/i, reason: 'Log file' },
  { id: 'private-key', pattern: /\.(pem|key|pfx|p12)$/i, reason: 'Private key or certificate material' },
  { id: 'fixture', pattern: /(^|\/)fixtures?\//i, reason: 'Test fixture directory' },
  { id: 'test-file', pattern: /(^|\/)test\//i, reason: 'Test directory' },
  { id: 'coverage', pattern: /(^|\/)coverage\//i, reason: 'Coverage output' },
  { id: 'git-dir', pattern: /(^|\/)\.git(\/|$)/i, reason: 'Git metadata' },
  { id: 'source-map', pattern: /\.map$/i, reason: 'Source map', allowWhenApproved: true },
];

export function inspectPackageBoundary(rootDir, options = {}) {
  const includeSourceMaps = options.includeSourceMaps ?? false;
  const violations = [];
  const files = [];

  function walk(current, relative = '') {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const rel = relative ? `${relative}/${entry.name}` : entry.name;
      const abs = path.join(current, entry.name);
      if (entry.isDirectory()) {
        walk(abs, rel);
        continue;
      }
      files.push(rel.replace(/\\/g, '/'));
      for (const rule of FORBIDDEN_PATTERNS) {
        if (rule.allowWhenApproved && includeSourceMaps && rule.id === 'source-map') {
          continue;
        }
        if (shouldSkipForbiddenRule(rule.id, rel)) {
          continue;
        }
        if (rule.pattern.test(rel) || rule.pattern.test(entry.name)) {
          violations.push({ path: rel, rule: rule.id, reason: rule.reason });
        }
      }
    }
  }

  if (!fs.existsSync(rootDir)) {
    return { ok: false, violations: [{ path: rootDir, rule: 'missing-root', reason: 'Package root missing' }], files: [] };
  }
  walk(rootDir);
  return { ok: violations.length === 0, violations, files };
}

export function assertPathTraversalSafe(entryPath) {
  const normalized = entryPath.replace(/\\/g, '/');
  if (normalized.includes('..') || path.isAbsolute(normalized)) {
    throw new Error(`Path traversal rejected: ${entryPath}`);
  }
  return normalized;
}

function isThirdPartyDependencyPath(relativePath) {
  return /(^|\/)node_modules\//i.test(relativePath.replace(/\\/g, '/'));
}

function shouldSkipForbiddenRule(ruleId, relativePath) {
  if (ruleId !== 'test-file' && ruleId !== 'fixture') {
    return false;
  }
  return isThirdPartyDependencyPath(relativePath);
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/g, '/')}`) {
  const root = process.argv[2];
  if (!root) {
    console.error('Usage: node package-boundary.mjs <artifact-root>');
    process.exit(2);
  }
  const result = inspectPackageBoundary(root);
  if (!result.ok) {
    console.error('Packaging boundary violations:');
    for (const v of result.violations) {
      console.error(` - [${v.rule}] ${v.path}: ${v.reason}`);
    }
    process.exit(1);
  }
  console.log(`Packaging boundary OK (${result.files.length} files)`);
}
