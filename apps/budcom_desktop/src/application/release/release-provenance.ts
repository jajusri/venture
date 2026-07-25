/** Generated build outputs that may change during a release without invalidating source provenance. */
export const ALLOWLISTED_GENERATED_PATH_PREFIXES = [
  'apps/budcom_desktop/dist/',
] as const;

export interface GitPorcelainEntry {
  readonly indexStatus: string;
  readonly workTreeStatus: string;
  readonly staged: boolean;
  readonly untracked: boolean;
  readonly path: string;
}

export interface ReleaseProvenanceSnapshot {
  readonly headCommit: string;
  readonly porcelainEntries: readonly GitPorcelainEntry[];
  readonly sourceTreeCleanAtStart: boolean;
}

export interface ReleaseProvenanceMetadata {
  readonly sourceTreeCleanAtStart: boolean;
  readonly dirtyTree: boolean;
  readonly gitCommit: string;
  readonly allowlistedGeneratedPaths: readonly string[];
  readonly generatedChangesAfterBuild?: readonly string[];
}

export interface GitCommandRunner {
  readonly statusPorcelain: () => string;
  readonly revParseHead: () => string;
}

export class ReleaseProvenanceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ReleaseProvenanceError';
  }
}

export function normalizeRepoPath(input: string): string {
  const trimmed = input.trim().replace(/\\/g, '/');
  if (!trimmed || trimmed.startsWith('/') || /^[a-zA-Z]:\//.test(trimmed)) {
    throw new ReleaseProvenanceError(`Absolute or empty repository path rejected: ${input}`);
  }
  if (trimmed.includes('..')) {
    throw new ReleaseProvenanceError(`Path traversal rejected: ${input}`);
  }
  return trimmed;
}

export function isAllowlistedGeneratedPath(
  repoRelativePath: string,
  allowlist: readonly string[] = ALLOWLISTED_GENERATED_PATH_PREFIXES,
): boolean {
  const normalized = normalizeRepoPath(repoRelativePath);
  return allowlist.some((prefixRaw) => {
    const prefix = normalizeRepoPath(prefixRaw.endsWith('/') ? prefixRaw : `${prefixRaw}/`);
    return normalized === prefix.slice(0, -1) || normalized.startsWith(prefix);
  });
}

export function parseGitPorcelain(output: string): GitPorcelainEntry[] {
  if (typeof output !== 'string') {
    throw new ReleaseProvenanceError('Git status parsing failed: output is not a string');
  }
  const normalized = output.replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\s+$/, '');
  if (!normalized) {
    return [];
  }
  const entries: GitPorcelainEntry[] = [];
  for (const line of normalized.split('\n')) {
    const match = line.match(/^(.)(.)\s+(.+)$/);
    if (!match) {
      throw new ReleaseProvenanceError(`Malformed git status line: ${line}`);
    }
    const indexStatus = match[1] ?? ' ';
    const workTreeStatus = match[2] ?? ' ';
    let filePath = match[3] ?? '';
    if (filePath.includes(' -> ')) {
      filePath = filePath.split(' -> ').pop() ?? filePath;
    }
    entries.push({
      indexStatus,
      workTreeStatus,
      staged: indexStatus !== ' ' && indexStatus !== '?',
      untracked: indexStatus === '?' && workTreeStatus === '?',
      path: normalizeRepoPath(filePath),
    });
  }
  return entries;
}

export function captureReleaseProvenanceSnapshot(
  git: GitCommandRunner,
): ReleaseProvenanceSnapshot {
  let headCommit: string;
  let porcelainOutput: string;
  try {
    headCommit = git.revParseHead().trim();
    porcelainOutput = git.statusPorcelain();
  } catch (error) {
    throw new ReleaseProvenanceError(
      `Git status parsing failed: ${error instanceof Error ? error.message : String(error)}`,
    );
  }
  if (!/^[0-9a-f]{40}$/i.test(headCommit)) {
    throw new ReleaseProvenanceError(`Invalid HEAD commit: ${headCommit}`);
  }
  const porcelainEntries = parseGitPorcelain(porcelainOutput);
  return {
    headCommit,
    porcelainEntries,
    sourceTreeCleanAtStart: porcelainEntries.length === 0,
  };
}

export function assertReleaseStartClean(snapshot: ReleaseProvenanceSnapshot): void {
  if (snapshot.sourceTreeCleanAtStart) {
    return;
  }
  const offenders = snapshot.porcelainEntries.map((entry) => entry.path);
  if (snapshot.porcelainEntries.some((entry) => entry.staged)) {
    throw new ReleaseProvenanceError(
      `Release rejected: staged changes present at pipeline start (${offenders.join(', ')})`,
    );
  }
  throw new ReleaseProvenanceError(
    `Release rejected: repository is dirty at pipeline start (${offenders.join(', ')})`,
  );
}

export function validatePostBuildProvenance(
  startSnapshot: ReleaseProvenanceSnapshot,
  endSnapshot: ReleaseProvenanceSnapshot,
  allowlist: readonly string[] = ALLOWLISTED_GENERATED_PATH_PREFIXES,
): readonly string[] {
  if (endSnapshot.headCommit !== startSnapshot.headCommit) {
    throw new ReleaseProvenanceError('Release rejected: HEAD changed during the pipeline');
  }
  if (!startSnapshot.sourceTreeCleanAtStart) {
    throw new ReleaseProvenanceError('Release rejected: source tree was not clean at pipeline start');
  }

  const generatedChanges: string[] = [];
  for (const entry of endSnapshot.porcelainEntries) {
    if (entry.staged) {
      throw new ReleaseProvenanceError(
        `Release rejected: staged change detected after build (${entry.path})`,
      );
    }
    if (!isAllowlistedGeneratedPath(entry.path, allowlist)) {
      throw new ReleaseProvenanceError(
        `Release rejected: non-allowlisted change detected (${entry.path})`,
      );
    }
    generatedChanges.push(entry.path);
  }
  return generatedChanges;
}

export function buildReleaseProvenanceMetadata(
  startSnapshot: ReleaseProvenanceSnapshot,
  generatedChangesAfterBuild: readonly string[],
  allowlist: readonly string[] = ALLOWLISTED_GENERATED_PATH_PREFIXES,
): ReleaseProvenanceMetadata {
  return {
    sourceTreeCleanAtStart: startSnapshot.sourceTreeCleanAtStart,
    dirtyTree: !startSnapshot.sourceTreeCleanAtStart,
    gitCommit: startSnapshot.headCommit,
    allowlistedGeneratedPaths: [...allowlist],
    generatedChangesAfterBuild: generatedChangesAfterBuild.length > 0
      ? [...generatedChangesAfterBuild]
      : undefined,
  };
}

export function resolveDirtyTreeFromProvenance(
  provenance: Pick<ReleaseProvenanceMetadata, 'sourceTreeCleanAtStart'> | null | undefined,
  fallbackDirtyTree: boolean,
): boolean {
  if (provenance && typeof provenance.sourceTreeCleanAtStart === 'boolean') {
    return !provenance.sourceTreeCleanAtStart;
  }
  return fallbackDirtyTree;
}
