import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import type { PathContainmentFs } from '../../../src/infrastructure/security/path-containment.js';
import { InboundXmlReasonCode } from '../../../src/ingestion/inbound-xml-reason-codes.js';
import {
  type InboundXmlFileSourceFs,
  readStableInboundXmlFile,
} from '../../../src/ingestion/inbound-xml-file-source.js';

type WatchFolderFs = InboundXmlFileSourceFs & PathContainmentFs;

const tempDirs: string[] = [];

afterEach(() => {
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

function makeFs(overrides: Partial<InboundXmlFileSourceFs> = {}): InboundXmlFileSourceFs {
  const baseStat = {
    isFile: () => true,
    isDirectory: () => false,
    isSymbolicLink: () => false,
    size: 100,
    mtimeMs: 1_000,
  };
  return {
    existsSync: () => true,
    realpathSync: (filePath) => filePath,
    lstatSync: () => baseStat,
    readFileSync: () => Buffer.from('<ENVELOPE></ENVELOPE>', 'utf8'),
    ...overrides,
  };
}

describe('readStableInboundXmlFile', () => {
  it('rejects null-byte paths', async () => {
    await expect(
      readStableInboundXmlFile({
        filePath: 'C:\\imports\\bad\u0000.xml',
        sourceType: 'trusted_internal_file',
      }),
    ).rejects.toMatchObject({ reasonCode: InboundXmlReasonCode.PathNullByte });
  });

  it('rejects symbolic links', async () => {
    await expect(
      readStableInboundXmlFile({
        filePath: 'C:\\imports\\link.xml',
        sourceType: 'trusted_internal_file',
        fsImpl: makeFs({
          lstatSync: () => ({
            isFile: () => false,
            isDirectory: () => false,
            isSymbolicLink: () => true,
            size: 10,
            mtimeMs: 1,
          }),
        }),
      }),
    ).rejects.toMatchObject({ reasonCode: InboundXmlReasonCode.SymlinkRejected });
  });

  it('rejects directories', async () => {
    await expect(
      readStableInboundXmlFile({
        filePath: 'C:\\imports\\folder',
        sourceType: 'trusted_internal_file',
        fsImpl: makeFs({
          lstatSync: () => ({
            isFile: () => false,
            isDirectory: () => true,
            isSymbolicLink: () => false,
            size: 0,
            mtimeMs: 1,
          }),
        }),
      }),
    ).rejects.toMatchObject({ reasonCode: InboundXmlReasonCode.NotRegularFile });
  });

  it('rejects trusted-internal files that change during read', async () => {
    let reads = 0;
    await expect(
      readStableInboundXmlFile({
        filePath: 'C:\\imports\\race.xml',
        sourceType: 'trusted_internal_file',
        fsImpl: makeFs({
          lstatSync: () => ({
            isFile: () => true,
            isDirectory: () => false,
            isSymbolicLink: () => false,
            size: reads++ === 0 ? 100 : 200,
            mtimeMs: 1_000,
          }),
        }),
      }),
    ).rejects.toMatchObject({ reasonCode: InboundXmlReasonCode.FileUnstable });
  });

  it('accepts stable trusted-internal files without a producer-completion window', async () => {
    const result = await readStableInboundXmlFile({
      filePath: 'C:\\imports\\stable.xml',
      sourceType: 'trusted_internal_file',
      fsImpl: makeFs(),
    });
    expect(result.rawBytes.toString('utf8')).toContain('ENVELOPE');
    expect(result.sourceIdentifier).toBe('stable.xml');
  });

  it('rejects watched-folder files that keep changing until timeout', async () => {
    const root = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'venture-watch-root-')));
    tempDirs.push(root);
    const filePath = path.join(root, 'writer.xml');
    fs.writeFileSync(filePath, '<ENVELOPE></ENVELOPE>', 'utf8');
    let observation = 0;
    const fsImpl: WatchFolderFs = {
      existsSync: fs.existsSync.bind(fs),
      realpathSync: fs.realpathSync.bind(fs),
      readFileSync: (target) => fs.readFileSync(target),
      lstatSync: (target) => {
        const stat = fs.lstatSync(target);
        observation += 1;
        return {
          isFile: () => stat.isFile(),
          isDirectory: () => stat.isDirectory(),
          isSymbolicLink: () => stat.isSymbolicLink(),
          size: stat.size + observation,
          mtimeMs: stat.mtimeMs + observation,
        };
      },
    };
    let tick = 0;
    await expect(
      readStableInboundXmlFile({
        filePath,
        sourceType: 'watched_folder_file',
        approvedRoot: root,
        stableDurationMs: 200,
        stabilityIntervalMs: 50,
        maxWaitMs: 300,
        now: () => tick++ * 50,
        sleep: async () => undefined,
        fsImpl,
      }),
    ).rejects.toMatchObject({ reasonCode: InboundXmlReasonCode.FileUnstable });
  });

  it('accepts watched-folder files after bounded stable-duration window', async () => {
    const root = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'venture-watch-stable-')));
    tempDirs.push(root);
    const filePath = path.join(root, 'settled.xml');
    fs.writeFileSync(filePath, '<ENVELOPE></ENVELOPE>', 'utf8');
    const fsImpl: WatchFolderFs = {
      existsSync: fs.existsSync.bind(fs),
      realpathSync: fs.realpathSync.bind(fs),
      readFileSync: (target) => fs.readFileSync(target),
      lstatSync: (target) => fs.lstatSync(target),
    };
    let tick = 0;
    const result = await readStableInboundXmlFile({
      filePath,
      sourceType: 'watched_folder_file',
      approvedRoot: root,
      stableDurationMs: 200,
      stabilityIntervalMs: 50,
      maxWaitMs: 1_000,
      now: () => {
        tick += 1;
        return tick * 50;
      },
      sleep: async () => undefined,
      fsImpl,
    });
    expect(result.rawBytes.length).toBeGreaterThan(0);
  });

  it('requires an approved root for watched-folder imports', async () => {
    await expect(
      readStableInboundXmlFile({
        filePath: 'C:\\imports\\file.xml',
        sourceType: 'watched_folder_file',
      }),
    ).rejects.toMatchObject({ reasonCode: InboundXmlReasonCode.SourceUnauthorized });
  });

  it('leaves trusted-internal source bytes unchanged on disk', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'venture-file-stability-'));
    tempDirs.push(dir);
    const filePath = path.join(dir, 'sample.xml');
    const original = '<ENVELOPE><BODY></BODY></ENVELOPE>';
    fs.writeFileSync(filePath, original, 'utf8');
    const before = fs.readFileSync(filePath);
    const result = await readStableInboundXmlFile({
      filePath,
      sourceType: 'trusted_internal_file',
    });
    expect(result.rawBytes.equals(Buffer.from(original, 'utf8'))).toBe(true);
    expect(fs.readFileSync(filePath).equals(before)).toBe(true);
  });
});
