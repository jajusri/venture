import {
  sanitizeLogContext,
  sanitizeLogMessage,
  type SafeLogContext,
} from '../privacy/log-context-sanitizer.js';

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface LogContext {
  readonly [key: string]: unknown;
}

export type SanitizedLogContext = SafeLogContext;

export interface StructuredLogEntry {
  readonly timestamp: string;
  readonly level: LogLevel;
  readonly message: string;
  readonly service: string;
  readonly context?: LogContext;
}

export interface Logger {
  debug(message: string, context?: LogContext): void;
  info(message: string, context?: LogContext): void;
  warn(message: string, context?: LogContext): void;
  error(message: string, context?: LogContext): void;
  child(context: LogContext): Logger;
}

export interface LoggerOptions {
  readonly service: string;
  readonly level: LogLevel;
  readonly sink?: (entry: StructuredLogEntry) => void;
}

const LEVEL_PRIORITY: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

export class StructuredLogger implements Logger {
  private readonly baseContext: LogContext;

  constructor(private readonly options: LoggerOptions) {
    this.baseContext = { service: options.service };
  }

  debug(message: string, context?: LogContext): void {
    this.write('debug', message, context);
  }

  info(message: string, context?: LogContext): void {
    this.write('info', message, context);
  }

  warn(message: string, context?: LogContext): void {
    this.write('warn', message, context);
  }

  error(message: string, context?: LogContext): void {
    this.write('error', message, context);
  }

  child(context: LogContext): Logger {
    return new StructuredLogger({
      ...this.options,
      service: this.options.service,
      sink: this.options.sink,
    }).withMergedContext(context);
  }

  private withMergedContext(context: LogContext): StructuredLogger {
    const logger = new StructuredLogger(this.options);
    Object.assign(logger.baseContext, context);
    return logger;
  }

  private write(level: LogLevel, message: string, context?: LogContext): void {
    if (LEVEL_PRIORITY[level] < LEVEL_PRIORITY[this.options.level]) {
      return;
    }

    const mergedContext = { ...this.baseContext, ...context };
    const entry: StructuredLogEntry = {
      timestamp: new Date().toISOString(),
      level,
      message: sanitizeLogMessage(message),
      service: this.options.service,
      context: sanitizeLogContext(mergedContext),
    };

    const sink = this.options.sink ?? defaultSink;
    sink(entry);
  }
}

function defaultSink(entry: StructuredLogEntry): void {
  const line = JSON.stringify(entry);
  if (entry.level === 'error' || entry.level === 'warn') {
    console.error(line);
    return;
  }
  console.log(line);
}

export function createLogger(options: LoggerOptions): Logger {
  return new StructuredLogger(options);
}
