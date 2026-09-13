import fs from 'node:fs';
import path from 'node:path';

export interface FileLogWriterOptions {
  readonly logsDir: string;
  readonly fileName?: string;
  readonly maxFileBytes?: number;
  readonly maxFiles?: number;
  readonly fsImpl?: Pick<typeof fs, 'appendFileSync' | 'existsSync' | 'mkdirSync' | 'readdirSync' | 'statSync' | 'renameSync' | 'unlinkSync' | 'writeFileSync'>;
}

export class FileLogWriter {
  private readonly logsDir: string;
  private readonly fileName: string;
  private readonly maxFileBytes: number;
  private readonly maxFiles: number;
  private readonly fsImpl: NonNullable<FileLogWriterOptions['fsImpl']>;
  private writeDisabled = false;
  private disabledReason: string | null = null;

  constructor(options: FileLogWriterOptions) {
    this.logsDir = options.logsDir;
    this.fileName = options.fileName ?? 'venture-desktop.log';
    this.maxFileBytes = options.maxFileBytes ?? 1_048_576;
    this.maxFiles = options.maxFiles ?? 5;
    this.fsImpl = options.fsImpl ?? fs;
    this.ensureLogsDir();
  }

  getLogFilePath(): string {
    return path.join(this.logsDir, this.fileName);
  }

  isWritable(): boolean {
    return !this.writeDisabled;
  }

  getDisabledReason(): string | null {
    return this.disabledReason;
  }

  appendLine(line: string): void {
    if (this.writeDisabled) {
      return;
    }
    try {
      this.ensureLogsDir();
      this.rotateIfNeeded();
      this.fsImpl.appendFileSync(this.getLogFilePath(), `${line}\n`, 'utf8');
    } catch (error) {
      this.writeDisabled = true;
      this.disabledReason = error instanceof Error ? error.message : String(error);
    }
  }

  clearCurrentLog(): void {
    if (this.writeDisabled) {
      return;
    }
    try {
      this.ensureLogsDir();
      this.fsImpl.writeFileSync(this.getLogFilePath(), '', 'utf8');
    } catch (error) {
      this.writeDisabled = true;
      this.disabledReason = error instanceof Error ? error.message : String(error);
    }
  }

  private ensureLogsDir(): void {
    this.fsImpl.mkdirSync(this.logsDir, { recursive: true });
  }

  private rotateIfNeeded(): void {
    const currentPath = this.getLogFilePath();
    if (!this.fsImpl.existsSync(currentPath)) {
      return;
    }
    const stats = this.fsImpl.statSync(currentPath);
    if (stats.size < this.maxFileBytes) {
      return;
    }

    for (let index = this.maxFiles - 1; index >= 1; index -= 1) {
      const from = `${currentPath}.${index}`;
      const to = `${currentPath}.${index + 1}`;
      if (this.fsImpl.existsSync(from)) {
        if (index + 1 > this.maxFiles) {
          this.fsImpl.unlinkSync(from);
        } else {
          this.fsImpl.renameSync(from, to);
        }
      }
    }

    this.fsImpl.renameSync(currentPath, `${currentPath}.1`);
    this.fsImpl.writeFileSync(currentPath, '', 'utf8');
  }
}
