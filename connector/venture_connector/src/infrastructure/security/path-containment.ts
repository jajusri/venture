import fs from 'node:fs';
import path from 'node:path';

export interface PathContainmentFs {
  existsSync(filePath: string): boolean;
  lstatSync(filePath: string): {
    isSymbolicLink(): boolean;
    isDirectory(): boolean;
    isFile(): boolean;
  };
  realpathSync(filePath: string): string;
}

export interface AssertWriteTargetContainedOptions {
  readonly candidatePath: string;
  readonly rootPath: string;
  readonly fsImpl?: PathContainmentFs;
  readonly protectedPaths?: readonly string[];
}

const defaultFs: PathContainmentFs = {
  existsSync: fs.existsSync.bind(fs),
  lstatSync: fs.lstatSync.bind(fs),
  realpathSync: fs.realpathSync.bind(fs),
};

export function rejectPathWithNullBytes(input: string): void {
  if (input.includes('\0')) {
    throw new Error('Path contains invalid characters.');
  }
}

export function toPlatformComparablePath(value: string): string {
  return process.platform === 'win32' ? value.toLowerCase() : value;
}

export function isPathContainedInRoot(candidatePath: string, rootPath: string): boolean {
  const resolvedCandidate = path.resolve(candidatePath);
  const resolvedRoot = path.resolve(rootPath);
  const comparableCandidate = toPlatformComparablePath(resolvedCandidate);
  const comparableRoot = toPlatformComparablePath(resolvedRoot);
  const rootPrefix = comparableRoot.endsWith(path.sep)
    ? comparableRoot
    : `${comparableRoot}${path.sep}`;
  return comparableCandidate === comparableRoot || comparableCandidate.startsWith(rootPrefix);
}

function resolveRealPathForNonExisting(targetPath: string, fsImpl: PathContainmentFs): string {
  const parent = path.dirname(targetPath);
  const base = path.basename(targetPath);
  if (parent === targetPath) {
    throw new Error('Write target path could not be verified safely.');
  }
  if (!fsImpl.existsSync(parent)) {
    return path.join(resolveRealPathForNonExisting(parent, fsImpl), base);
  }
  return path.join(fsImpl.realpathSync(parent), base);
}

function assertNoSymlinksInExistingSegments(
  rootRealPath: string,
  targetPath: string,
  fsImpl: PathContainmentFs,
): void {
  const relative = path.relative(rootRealPath, targetPath);
  if (relative === '' || relative === '.') {
    return;
  }
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    return;
  }

  const segments = relative.split(path.sep).filter(Boolean);
  let current = rootRealPath;
  for (const segment of segments) {
    current = path.join(current, segment);
    if (!fsImpl.existsSync(current)) {
      break;
    }
    if (fsImpl.lstatSync(current).isSymbolicLink()) {
      throw new Error('Write target traverses a symbolic link or junction.');
    }
  }
}

export function assertNotProtectedWriteTarget(
  candidatePath: string,
  protectedPaths: readonly string[],
): void {
  const comparableCandidate = toPlatformComparablePath(path.resolve(candidatePath));
  for (const protectedPath of protectedPaths) {
    const comparableProtected = toPlatformComparablePath(path.resolve(protectedPath));
    if (comparableCandidate === comparableProtected) {
      throw new Error('Write target would overwrite a protected file.');
    }
  }
}

export function assertWriteTargetContained(options: AssertWriteTargetContainedOptions): string {
  const fsImpl = options.fsImpl ?? defaultFs;
  rejectPathWithNullBytes(options.candidatePath);
  rejectPathWithNullBytes(options.rootPath);

  const resolvedRoot = path.resolve(options.rootPath);
  const resolvedCandidate = path.resolve(options.candidatePath);

  if (!isPathContainedInRoot(resolvedCandidate, resolvedRoot)) {
    throw new Error('Write target must remain within the approved root directory.');
  }

  let rootRealPath: string;
  try {
    rootRealPath = fsImpl.realpathSync(resolvedRoot);
  } catch {
    throw new Error('Approved root directory could not be verified safely.');
  }

  if (!isPathContainedInRoot(resolvedCandidate, rootRealPath)) {
    throw new Error('Write target must remain within the approved root directory.');
  }

  assertNoSymlinksInExistingSegments(rootRealPath, resolvedCandidate, fsImpl);

  let verifiedTarget: string;
  try {
    verifiedTarget = fsImpl.existsSync(resolvedCandidate)
      ? fsImpl.realpathSync(resolvedCandidate)
      : resolveRealPathForNonExisting(resolvedCandidate, fsImpl);
  } catch {
    throw new Error('Write target path could not be verified safely.');
  }

  if (!isPathContainedInRoot(verifiedTarget, rootRealPath)) {
    throw new Error('Write target resolves outside the approved root directory.');
  }

  if (options.protectedPaths?.length) {
    assertNotProtectedWriteTarget(verifiedTarget, options.protectedPaths);
  }

  return resolvedCandidate;
}
