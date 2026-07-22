import type { LogEntry, LogLevel } from './types.js';

let sequence = 0;

export class LogService {
  private readonly entries: LogEntry[] = [];
  private readonly maxEntries: number;

  constructor(maxEntries = 500) {
    this.maxEntries = maxEntries;
  }

  append(level: LogLevel, message: string, timestamp = new Date().toISOString()): LogEntry {
    const entry: LogEntry = {
      id: `log-${++sequence}`,
      timestamp,
      level,
      message,
    };
    this.entries.unshift(entry);
    if (this.entries.length > this.maxEntries) {
      this.entries.length = this.maxEntries;
    }
    return entry;
  }

  getEntries(): readonly LogEntry[] {
    return [...this.entries];
  }

  clear(): void {
    this.entries.length = 0;
  }
}
