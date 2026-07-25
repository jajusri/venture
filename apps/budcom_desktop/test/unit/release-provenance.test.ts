import { describe, expect, it } from 'vitest';

import {
  ALLOWLISTED_GENERATED_PATH_PREFIXES,
  assertReleaseStartClean,
  buildReleaseProvenanceMetadata,
  captureReleaseProvenanceSnapshot,
  isAllowlistedGeneratedPath,
  normalizeRepoPath,
  parseGitPorcelain,
  ReleaseProvenanceError,
  resolveDirtyTreeFromProvenance,
  validatePostBuildProvenance,
  type GitCommandRunner,
} from '../../src/application/release/release-provenance.js';

const WORKTREE_MODIFIED = (filePath: string) => `\x20M ${filePath}`;
const STAGED = (filePath: string) => `M\x20 ${filePath}`;
const UNTRACKED = (filePath: string) => `?? ${filePath}`;

function gitRunner(overrides: Partial<GitCommandRunner>): GitCommandRunner {
  return {
    statusPorcelain: () => '',
    revParseHead: () => 'cd50685d632e6fd166cb80afcae43c0a36b2876a',
    ...overrides,
  };
}

describe('release provenance', () => {
  it('accepts clean start and only allowlisted dist changes after build', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({ statusPorcelain: () => '' }));
    assertReleaseStartClean(start);
    const end = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => [
        WORKTREE_MODIFIED('apps/budcom_desktop/dist/main/main.js'),
        WORKTREE_MODIFIED('apps/budcom_desktop/dist/preload/preload.js'),
      ].join('\n'),
    }));
    const generated = validatePostBuildProvenance(start, end);
    expect(generated).toEqual([
      'apps/budcom_desktop/dist/main/main.js',
      'apps/budcom_desktop/dist/preload/preload.js',
    ]);
    const metadata = buildReleaseProvenanceMetadata(start, generated);
    expect(metadata.sourceTreeCleanAtStart).toBe(true);
    expect(metadata.dirtyTree).toBe(false);
    expect(metadata.generatedChangesAfterBuild).toEqual(generated);
  });

  it('rejects dirty source file at start', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => WORKTREE_MODIFIED('apps/budcom_desktop/src/main/main.ts'),
    }));
    expect(() => assertReleaseStartClean(start)).toThrow(/dirty at pipeline start/);
    expect(start.sourceTreeCleanAtStart).toBe(false);
  });

  it('rejects untracked source file at start', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => UNTRACKED('apps/budcom_desktop/src/new-file.ts'),
    }));
    expect(() => assertReleaseStartClean(start)).toThrow(/dirty at pipeline start/);
  });

  it('rejects staged change at start', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => STAGED('apps/budcom_desktop/package.json'),
    }));
    expect(() => assertReleaseStartClean(start)).toThrow(/staged changes present at pipeline start/);
  });

  it('rejects HEAD changes during build', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({ statusPorcelain: () => '' }));
    const end = captureReleaseProvenanceSnapshot(gitRunner({
      revParseHead: () => 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      statusPorcelain: () => WORKTREE_MODIFIED('apps/budcom_desktop/dist/main/main.js'),
    }));
    expect(() => validatePostBuildProvenance(start, end)).toThrow(/HEAD changed/);
  });

  it('rejects non-dist tracked change during build', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({ statusPorcelain: () => '' }));
    const end = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => WORKTREE_MODIFIED('connector/budcom_connector/src/bootstrap/app.ts'),
    }));
    expect(() => validatePostBuildProvenance(start, end)).toThrow(/non-allowlisted change/);
  });

  it('rejects staged change after build', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({ statusPorcelain: () => '' }));
    const end = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => STAGED('apps/budcom_desktop/dist/main/main.js'),
    }));
    expect(() => validatePostBuildProvenance(start, end)).toThrow(/staged change detected after build/);
  });

  it('reports allowed dist changes explicitly', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({ statusPorcelain: () => '' }));
    const end = captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => UNTRACKED('apps/budcom_desktop/dist/application/release/build-info.js'),
    }));
    const generated = validatePostBuildProvenance(start, end);
    expect(generated).toEqual(['apps/budcom_desktop/dist/application/release/build-info.js']);
  });

  it('fails closed on malformed git status', () => {
    expect(() => parseGitPorcelain('bad')).toThrow(ReleaseProvenanceError);
    expect(() => parseGitPorcelain(' M')).toThrow(ReleaseProvenanceError);
  });

  it('rejects path-prefix lookalike bypass', () => {
    expect(isAllowlistedGeneratedPath('apps/budcom_desktop/dist_evil/main.js')).toBe(false);
    expect(isAllowlistedGeneratedPath('apps/budcom_desktop/dist/main.js')).toBe(true);
  });

  it('handles Windows path separators safely', () => {
    expect(normalizeRepoPath('apps\\budcom_desktop\\dist\\main\\main.js'))
      .toBe('apps/budcom_desktop/dist/main/main.js');
    expect(isAllowlistedGeneratedPath('apps\\budcom_desktop\\dist\\main\\main.js')).toBe(true);
  });

  it('derives dirtyTree from source provenance rather than post-build output', () => {
    expect(resolveDirtyTreeFromProvenance({ sourceTreeCleanAtStart: true }, true)).toBe(false);
    expect(resolveDirtyTreeFromProvenance({ sourceTreeCleanAtStart: false }, false)).toBe(true);
    expect(resolveDirtyTreeFromProvenance(null, true)).toBe(true);
  });

  it('includes allowlisted paths in metadata', () => {
    const start = captureReleaseProvenanceSnapshot(gitRunner({ statusPorcelain: () => '' }));
    const metadata = buildReleaseProvenanceMetadata(start, []);
    expect(metadata.allowlistedGeneratedPaths).toEqual([...ALLOWLISTED_GENERATED_PATH_PREFIXES]);
  });

  it('fails closed when git commands fail', () => {
    expect(() => captureReleaseProvenanceSnapshot(gitRunner({
      revParseHead: () => {
        throw new Error('git unavailable');
      },
    }))).toThrow(/Git status parsing failed/);

    expect(() => captureReleaseProvenanceSnapshot(gitRunner({
      statusPorcelain: () => {
        throw new Error('git status failed');
      },
    }))).toThrow(/Git status parsing failed/);
  });
});
