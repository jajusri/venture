import { mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';

export const AUDIT_LIFECYCLE_CODES = {
  audit_stat_failed: 'audit_stat_failed',
  audit_rotation_delete_failed: 'audit_rotation_delete_failed',
  audit_rotation_rename_failed: 'audit_rotation_rename_failed',
  audit_append_failed: 'audit_append_failed',
  audit_directory_create_failed: 'audit_directory_create_failed',
} as const;

export type AuditLifecycleCode = (typeof AUDIT_LIFECYCLE_CODES)[keyof typeof AUDIT_LIFECYCLE_CODES];

export interface AuditRotatorConfig {
  readonly basePath: string;
  readonly maxBytes: number;
  readonly maxFiles: number;
}

export interface AuditFileOperations {
  stat(filePath: string): Promise<{ readonly size: number } | 'missing'>;
  mkdir(dirPath: string): Promise<void>;
  append(filePath: string, content: string): Promise<void>;
  unlink(filePath: string): Promise<void>;
  rename(from: string, to: string): Promise<void>;
}

export function auditRotatedPath(basePath: string, slot: number): string {
  return `${basePath}.${slot}`;
}

export function shouldRotateBeforeAppend(
  currentSize: number,
  newLineBytes: number,
  maxBytes: number,
): boolean {
  return currentSize > 0 && currentSize + newLineBytes > maxBytes;
}

function isMissingError(error: unknown): boolean {
  return (
    typeof error === 'object'
    && error !== null
    && 'code' in error
    && (error as { code: string }).code === 'ENOENT'
  );
}

export class AuditFileRotator {
  constructor(
    private readonly config: AuditRotatorConfig,
    private readonly fs: AuditFileOperations,
    private readonly onLifecycleIssue: (code: AuditLifecycleCode) => void,
  ) {}

  async appendLine(lineWithNewline: string): Promise<void> {
    const newLineBytes = Buffer.byteLength(lineWithNewline, 'utf8');

    try {
      await this.fs.mkdir(dirname(this.config.basePath));
    } catch {
      this.onLifecycleIssue(AUDIT_LIFECYCLE_CODES.audit_directory_create_failed);
    }

    let currentSize = 0;
    try {
      const fileStat = await this.fs.stat(this.config.basePath);
      currentSize = fileStat === 'missing' ? 0 : fileStat.size;
    } catch {
      this.onLifecycleIssue(AUDIT_LIFECYCLE_CODES.audit_stat_failed);
      currentSize = 0;
    }

    if (shouldRotateBeforeAppend(currentSize, newLineBytes, this.config.maxBytes)) {
      await this.rotateFiles();
    }

    try {
      await this.fs.append(this.config.basePath, lineWithNewline);
    } catch {
      this.onLifecycleIssue(AUDIT_LIFECYCLE_CODES.audit_append_failed);
    }
  }

  async rotateFiles(): Promise<boolean> {
    const { basePath, maxFiles } = this.config;

    try {
      await this.fs.unlink(auditRotatedPath(basePath, maxFiles));
    } catch (error) {
      if (!isMissingError(error)) {
        this.onLifecycleIssue(AUDIT_LIFECYCLE_CODES.audit_rotation_delete_failed);
        return false;
      }
    }

    for (let slot = maxFiles - 1; slot >= 1; slot -= 1) {
      const from = auditRotatedPath(basePath, slot);
      const to = auditRotatedPath(basePath, slot + 1);
      try {
        const source = await this.fs.stat(from);
        if (source !== 'missing') {
          await this.fs.rename(from, to);
        }
      } catch {
        this.onLifecycleIssue(AUDIT_LIFECYCLE_CODES.audit_rotation_rename_failed);
        return false;
      }
    }

    try {
      const current = await this.fs.stat(basePath);
      if (current !== 'missing') {
        await this.fs.rename(basePath, auditRotatedPath(basePath, 1));
      }
    } catch {
      this.onLifecycleIssue(AUDIT_LIFECYCLE_CODES.audit_rotation_rename_failed);
      return false;
    }

    return true;
  }
}

export async function createNodeAuditFileOperations(): Promise<AuditFileOperations> {
  const fs = await import('node:fs/promises');
  return {
    async stat(filePath) {
      try {
        const result = await fs.stat(filePath);
        return { size: result.size };
      } catch (error) {
        if (isMissingError(error)) {
          return 'missing';
        }
        throw error;
      }
    },
    mkdir: async (dirPath) => {
      await mkdir(dirPath, { recursive: true });
    },
    append: (filePath, content) => fs.appendFile(filePath, content, 'utf8'),
    unlink: (filePath) => fs.unlink(filePath),
    rename: (from, to) => fs.rename(from, to),
  };
}
