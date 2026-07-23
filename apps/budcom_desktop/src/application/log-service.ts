import type { DesktopLogLevel } from './desktop-config-schema.js';
import { FileLogWriter } from './file-log-writer.js';
import { redactString } from './log-redaction.js';
import type { LogEntry, LogLevel } from './types.js';

let sequence = 0;

export interface StructuredLogMetadata {
  readonly event?: string;
  readonly component?: string;
  readonly processState?: string;
  readonly connectorOwnership?: 'desktop-managed' | 'external' | 'none';
  readonly attemptNumber?: number;
  readonly errorCode?: string;
  readonly [key: string]: string | number | boolean | null | undefined;
}

export interface StructuredLogInput {
  readonly level: LogLevel;
  readonly message: string;
  readonly event?: string;
  readonly component?: string;
  readonly metadata?: StructuredLogMetadata;
  readonly timestamp?: string;
}

const LOG_LEVEL_ORDER: Record<DesktopLogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

function mapDesktopLevel(level: LogLevel): DesktopLogLevel {
  switch (level) {
    case 'warning':
      return 'warn';
    case 'error':
      return 'error';
    default:
      return 'info';
  }
}

function sanitizeMetadata(metadata: StructuredLogMetadata): Record<string, string | number | boolean | null> {
  const output: Record<string, string | number | boolean | null> = {};
  for (const [key, value] of Object.entries(metadata)) {
    if (value !== undefined) {
      output[key] = value;
    }
  }
  return output;
}

export class LogService {
  private readonly entries: LogEntry[] = [];
  private readonly maxEntries: number;
  private readonly fileWriter: FileLogWriter | null;
  private minimumLevel: DesktopLogLevel;
  private consoleEnabled: boolean;

  constructor(options?: {
    maxEntries?: number;
    fileWriter?: FileLogWriter | null;
    minimumLevel?: DesktopLogLevel;
    consoleEnabled?: boolean;
  }) {
    this.maxEntries = options?.maxEntries ?? 500;
    this.fileWriter = options?.fileWriter ?? null;
    this.minimumLevel = options?.minimumLevel ?? 'info';
    this.consoleEnabled = options?.consoleEnabled ?? process.env.NODE_ENV !== 'production';
  }

  setMinimumLevel(level: DesktopLogLevel): void {
    this.minimumLevel = level;
  }

  append(level: LogLevel, message: string, timestamp = new Date().toISOString()): LogEntry {
    return this.appendStructured({
      level,
      message,
      timestamp,
    });
  }

  appendStructured(input: StructuredLogInput): LogEntry {
    const sanitizedMessage = redactString(input.message);
    const desktopLevel = mapDesktopLevel(input.level);
    const metadata = input.metadata ? sanitizeMetadata(input.metadata) : null;

    if (LOG_LEVEL_ORDER[desktopLevel] < LOG_LEVEL_ORDER[this.minimumLevel]) {
      return {
        id: `log-${++sequence}`,
        timestamp: input.timestamp ?? new Date().toISOString(),
        level: input.level,
        message: sanitizedMessage,
        event: input.event ?? null,
        component: input.component ?? null,
        metadata,
      };
    }

    const entry: LogEntry = {
      id: `log-${++sequence}`,
      timestamp: input.timestamp ?? new Date().toISOString(),
      level: input.level,
      message: sanitizedMessage,
      event: input.event ?? null,
      component: input.component ?? null,
      metadata,
    };

    this.entries.unshift(entry);
    if (this.entries.length > this.maxEntries) {
      this.entries.length = this.maxEntries;
    }

    const filePayload = JSON.stringify({
      timestamp: entry.timestamp,
      level: mapDesktopLevel(entry.level),
      event: entry.event,
      component: entry.component,
      message: entry.message,
      metadata: entry.metadata,
    });
    this.fileWriter?.appendLine(filePayload);

    if (this.consoleEnabled) {
      const prefix = `[${entry.component ?? 'desktop'}:${entry.event ?? 'log'}]`;
      console.error(prefix, entry.message);
    }

    return entry;
  }

  getEntries(): readonly LogEntry[] {
    return [...this.entries];
  }

  getLifecycleEvents(limit = 50): readonly LogEntry[] {
    return this.entries.filter((entry) => entry.message.includes('[lifecycle:') || entry.event?.startsWith('lifecycle')).slice(0, limit);
  }

  getRecentErrors(limit = 25): readonly LogEntry[] {
    return this.entries.filter((entry) => entry.level === 'error' || entry.level === 'warning').slice(0, limit);
  }

  clear(): void {
    this.entries.length = 0;
  }

  clearNonessential(): void {
    this.entries.splice(0, this.entries.length, ...this.entries.filter((entry) => entry.level === 'error'));
  }

  getLogFilePath(): string | null {
    return this.fileWriter?.getLogFilePath() ?? null;
  }

  isFileLoggingAvailable(): boolean {
    return this.fileWriter?.isWritable() ?? false;
  }

  getFileLoggingDisabledReason(): string | null {
    return this.fileWriter?.getDisabledReason() ?? null;
  }
}
