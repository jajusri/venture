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
] as const;

export interface PackageBoundaryViolation {
  readonly path: string;
  readonly rule: string;
  readonly reason: string;
}

export interface PackageBoundaryResult {
  readonly ok: boolean;
  readonly violations: readonly PackageBoundaryViolation[];
  readonly files: readonly string[];
}

export interface PackageBoundaryFs {
  existsSync(path: string): boolean;
  readdirSync(path: string, options: { withFileTypes: true }): Array<{
    name: string;
    isDirectory(): boolean;
  }>;
}

export function assertPathTraversalSafe(entryPath: string): string {
  const normalized = entryPath.replace(/\\/g, '/');
  if (normalized.includes('..') || normalized.startsWith('/')) {
    throw new Error(`Path traversal rejected: ${entryPath}`);
  }
  return normalized;
}

export function inspectPackageBoundary(
  rootDir: string,
  fsImpl: PackageBoundaryFs,
  options: { includeSourceMaps?: boolean } = {},
): PackageBoundaryResult {
  const includeSourceMaps = options.includeSourceMaps ?? false;
  const violations: PackageBoundaryViolation[] = [];
  const files: string[] = [];

  function walk(current: string, relative = ''): void {
    for (const entry of fsImpl.readdirSync(current, { withFileTypes: true })) {
      const rel = relative ? `${relative}/${entry.name}` : entry.name;
      const abs = `${current}/${entry.name}`;
      if (entry.isDirectory()) {
        walk(abs, rel);
        continue;
      }
      files.push(rel.replace(/\\/g, '/'));
      for (const rule of FORBIDDEN_PATTERNS) {
        if ('allowWhenApproved' in rule && rule.allowWhenApproved && includeSourceMaps && rule.id === 'source-map') {
          continue;
        }
        if (rule.pattern.test(rel) || rule.pattern.test(entry.name)) {
          violations.push({ path: rel, rule: rule.id, reason: rule.reason });
        }
      }
    }
  }

  if (!fsImpl.existsSync(rootDir)) {
    return {
      ok: false,
      violations: [{ path: rootDir, rule: 'missing-root', reason: 'Package root missing' }],
      files: [],
    };
  }
  walk(rootDir);
  return { ok: violations.length === 0, violations, files };
}
