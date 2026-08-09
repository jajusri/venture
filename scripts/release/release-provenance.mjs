/** Generated build outputs that may change during a release without invalidating source provenance. */
import { execSync } from 'node:child_process';

export const ALLOWLISTED_GENERATED_PATH_PREFIXES = [
  'apps/budcom_desktop/dist/',
  'release/controlled-pilot/0.4.3/STALE-DO-NOT-DISTRIBUTE.md',
];

export class ReleaseProvenanceError extends Error {
  constructor(message) {
    super(message);
    this.name = 'ReleaseProvenanceError';
  }
}

export function normalizeRepoPath(input) {
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
  repoRelativePath,
  allowlist = ALLOWLISTED_GENERATED_PATH_PREFIXES,
) {
  const normalized = normalizeRepoPath(repoRelativePath);
  return allowlist.some((prefixRaw) => {
    if (!prefixRaw.endsWith('/')) {
      return normalized === normalizeRepoPath(prefixRaw);
    }
    const prefix = normalizeRepoPath(prefixRaw);
    return normalized === prefix.slice(0, -1) || normalized.startsWith(prefix);
  });
}

export function parseGitPorcelain(output) {
  if (typeof output !== 'string') {
    throw new ReleaseProvenanceError('Git status parsing failed: output is not a string');
  }
  const normalized = output.replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\s+$/, '');
  if (!normalized) {
    return [];
  }
  const entries = [];
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

export function captureReleaseProvenanceSnapshot(git) {
  let headCommit;
  let porcelainOutput;
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

export function assertReleaseStartClean(snapshot) {
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
  startSnapshot,
  endSnapshot,
  allowlist = ALLOWLISTED_GENERATED_PATH_PREFIXES,
) {
  if (endSnapshot.headCommit !== startSnapshot.headCommit) {
    throw new ReleaseProvenanceError('Release rejected: HEAD changed during the pipeline');
  }
  if (!startSnapshot.sourceTreeCleanAtStart) {
    throw new ReleaseProvenanceError('Release rejected: source tree was not clean at pipeline start');
  }

  const generatedChanges = [];
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
  startSnapshot,
  generatedChangesAfterBuild,
  allowlist = ALLOWLISTED_GENERATED_PATH_PREFIXES,
) {
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

export function resolveDirtyTreeFromProvenance(provenance, fallbackDirtyTree) {
  if (provenance && typeof provenance.sourceTreeCleanAtStart === 'boolean') {
    return !provenance.sourceTreeCleanAtStart;
  }
  return fallbackDirtyTree;
}

export function createRepoGitRunner(repoRoot) {
  return {
    statusPorcelain: () => execSync('git status --porcelain', { cwd: repoRoot, encoding: 'utf8' }),
    revParseHead: () => execSync('git rev-parse HEAD', { cwd: repoRoot, encoding: 'utf8' }),
  };
}
