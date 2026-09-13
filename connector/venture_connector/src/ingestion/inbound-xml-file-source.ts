import fs from 'node:fs';
import path from 'node:path';

import { assertWriteTargetContained, rejectPathWithNullBytes } from '../infrastructure/security/path-containment.js';
import { InboundXmlReasonCode } from './inbound-xml-reason-codes.js';

export interface InboundXmlFileSourceFs {
  existsSync(filePath: string): boolean;
  realpathSync(filePath: string): string;
  lstatSync(filePath: string): {
    isFile(): boolean;
    isDirectory(): boolean;
    isSymbolicLink(): boolean;
    size: number;
    mtimeMs: number;
  };
  readFileSync(filePath: string): Buffer;
}

export interface ReadStableInboundFileOptions {
  readonly filePath: string;
  readonly sourceType: 'trusted_internal_file' | 'watched_folder_file';
  readonly approvedRoot?: string;
  /** Watched-folder only: interval between stability observations. */
  readonly stabilityIntervalMs?: number;
  /** Watched-folder only: required stable duration before read. */
  readonly stableDurationMs?: number;
  /** Watched-folder only: maximum wait before rejecting unstable files. */
  readonly maxWaitMs?: number;
  readonly now?: () => number;
  readonly sleep?: (ms: number) => Promise<void>;
  readonly fsImpl?: InboundXmlFileSourceFs;
}

const defaultFs: InboundXmlFileSourceFs = {
  existsSync: fs.existsSync.bind(fs),
  realpathSync: fs.realpathSync.bind(fs),
  lstatSync: fs.lstatSync.bind(fs),
  readFileSync: (filePath) => fs.readFileSync(filePath),
};

/**
 * Reads an inbound XML file into an immutable in-memory snapshot.
 *
 * Guarantee scope:
 * - trusted_internal_file: assumes the caller already selected a stable file (no OS picker
 *   exists yet). Performs a single read with post-read metadata verification only.
 * - watched_folder_file: bounded stability window (default 500 ms over 5 s max wait)
 *   before snapshot read; production watched-folder requires this stronger protocol.
 */
export async function readStableInboundXmlFile(
  options: ReadStableInboundFileOptions,
): Promise<{ readonly rawBytes: Buffer; readonly sourceIdentifier: string }> {
  const fsImpl = options.fsImpl ?? defaultFs;
  try {
    rejectPathWithNullBytes(options.filePath);
  } catch {
    throw fileSourceError(
      InboundXmlReasonCode.PathNullByte,
      'Inbound XML source path contains invalid characters.',
    );
  }

  if (options.sourceType === 'watched_folder_file') {
    if (!options.approvedRoot) {
      throw fileSourceError(
        InboundXmlReasonCode.SourceUnauthorized,
        'Watched-folder imports require an approved root directory.',
      );
    }
    try {
      assertWriteTargetContained({
        candidatePath: options.filePath,
        rootPath: options.approvedRoot,
        fsImpl,
      });
    } catch {
      throw fileSourceError(
        InboundXmlReasonCode.PathOutsideRoot,
        'Inbound XML source must remain within the approved root directory.',
      );
    }
  }

  const resolved = path.resolve(options.filePath);
  if (!fsImpl.existsSync(resolved)) {
    throw fileSourceError(InboundXmlReasonCode.FileMissing, 'Inbound XML source file is missing.');
  }

  const stat = assertRegularFile(fsImpl.lstatSync(resolved));

  if (options.sourceType === 'watched_folder_file') {
    await waitForWatchedFolderStability(resolved, fsImpl, options);
  }

  const beforeRead = fsImpl.lstatSync(resolved);
  const rawBytes = Buffer.from(fsImpl.readFileSync(resolved));
  const afterRead = fsImpl.lstatSync(resolved);
  if (
    afterRead.size !== beforeRead.size
    || afterRead.mtimeMs !== beforeRead.mtimeMs
    || afterRead.size !== stat.size
  ) {
    throw fileSourceError(
      InboundXmlReasonCode.FileUnstable,
      'Inbound XML source changed during read and was rejected.',
    );
  }

  return {
    rawBytes,
    sourceIdentifier: path.basename(resolved),
  };
}

async function waitForWatchedFolderStability(
  filePath: string,
  fsImpl: InboundXmlFileSourceFs,
  options: ReadStableInboundFileOptions,
): Promise<void> {
  const intervalMs = options.stabilityIntervalMs ?? 100;
  const stableDurationMs = options.stableDurationMs ?? 500;
  const maxWaitMs = options.maxWaitMs ?? 5_000;
  const sleep = options.sleep ?? ((ms: number) => new Promise((resolve) => setTimeout(resolve, ms)));
  const now = options.now ?? (() => Date.now());

  const deadline = now() + maxWaitMs;
  let stableSince = now();
  let previous = fsImpl.lstatSync(filePath);

  while (now() <= deadline) {
    await sleep(intervalMs);
    const current = fsImpl.lstatSync(filePath);
    if (current.size !== previous.size || current.mtimeMs !== previous.mtimeMs) {
      stableSince = now();
      previous = current;
      continue;
    }
    if (now() - stableSince >= stableDurationMs) {
      return;
    }
  }

  throw fileSourceError(
    InboundXmlReasonCode.FileUnstable,
    'Inbound XML source did not become stable within the bounded wait window.',
  );
}

function assertRegularFile(stat: ReturnType<InboundXmlFileSourceFs['lstatSync']>) {
  if (stat.isSymbolicLink()) {
    throw fileSourceError(
      InboundXmlReasonCode.SymlinkRejected,
      'Inbound XML source must not be a symbolic link or junction.',
    );
  }
  if (stat.isDirectory()) {
    throw fileSourceError(InboundXmlReasonCode.NotRegularFile, 'Inbound XML source must be a regular file.');
  }
  if (!stat.isFile()) {
    throw fileSourceError(InboundXmlReasonCode.NotRegularFile, 'Inbound XML source must be a regular file.');
  }
  return stat;
}

function fileSourceError(reasonCode: InboundXmlReasonCode, message: string): Error {
  const error = new Error(message);
  (error as Error & { reasonCode: InboundXmlReasonCode }).reasonCode = reasonCode;
  return error;
}

export function extractReasonCode(error: unknown): string | undefined {
  if (typeof error === 'object' && error !== null && 'reasonCode' in error) {
    const value = (error as { reasonCode?: unknown }).reasonCode;
    return typeof value === 'string' ? value : undefined;
  }
  return undefined;
}
