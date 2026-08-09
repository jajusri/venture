import fs from 'node:fs';
import path from 'node:path';

export type StartupDiagnosticStage =
  | 'process_start'
  | 'single_instance_lock'
  | 'single_instance_denied_exit'
  | 'main_module_loaded'
  | 'release_mode_resolved'
  | 'app_data_ready'
  | 'connector_transport_identity_migration'
  | 'uncaught_exception'
  | 'unhandled_rejection'
  | 'app_ready'
  | 'window_creation_start'
  | 'renderer_path_selected'
  | 'window_created'
  | 'ready_to_show'
  | 'renderer_loaded'
  | 'did_fail_load'
  | 'render_process_gone'
  | 'window_closed'
  | 'before_quit'
  | 'will_quit'
  | 'window_all_closed'
  | 'explicit_app_quit'
  | 'explicit_process_exit'
  | 'connector_spawn_attempt'
  | 'connector_spawned'
  | 'connector_child_exit'
  | 'connector_child_stderr'
  | 'connector_health_check'
  | 'connector_startup_failure'
  | 'packaged_runtime_integrity_failure'
  | 'bind_integrity_failure'
  | 'bootstrap_error';

export interface StartupDiagnosticEntry {
  readonly timestamp: string;
  readonly stage: StartupDiagnosticStage;
  readonly detail?: Record<string, string | number | boolean | null>;
}

export interface StartupDiagnosticsOptions {
  readonly logsDir: string;
  readonly fileName?: string;
  readonly maxFileBytes?: number;
  readonly maxFiles?: number;
  readonly fsImpl?: Pick<
    typeof fs,
    'appendFileSync' | 'existsSync' | 'mkdirSync' | 'readdirSync' | 'readFileSync' | 'renameSync' | 'statSync' | 'unlinkSync' | 'writeFileSync'
  >;
}

const STARTUP_READY_STAGES = new Set<StartupDiagnosticStage>([
  'renderer_loaded',
  'ready_to_show',
]);

export class StartupDiagnostics {
  private readonly logsDir: string;
  private readonly fileName: string;
  private readonly maxFileBytes: number;
  private readonly maxFiles: number;
  private readonly fsImpl: NonNullable<StartupDiagnosticsOptions['fsImpl']>;
  private disabled = false;
  private startupReadyRecorded = false;

  constructor(options: StartupDiagnosticsOptions) {
    this.logsDir = options.logsDir;
    this.fileName = options.fileName ?? 'startup-diagnostics.jsonl';
    this.maxFileBytes = options.maxFileBytes ?? 512_000;
    this.maxFiles = options.maxFiles ?? 3;
    this.fsImpl = options.fsImpl ?? fs;
  }

  getLogPath(): string {
    return path.join(this.logsDir, this.fileName);
  }

  hasStartupReadySignal(): boolean {
    if (this.startupReadyRecorded) {
      return true;
    }
    try {
      if (!this.fsImpl.existsSync(this.getLogPath())) {
        return false;
      }
      const content = this.fsImpl.readFileSync(this.getLogPath(), 'utf8');
      for (const line of content.split(/\r?\n/)) {
        if (line.trim().length === 0) {
          continue;
        }
        const parsed = JSON.parse(line) as StartupDiagnosticEntry;
        if (STARTUP_READY_STAGES.has(parsed.stage)) {
          return true;
        }
      }
    } catch {
      return false;
    }
    return false;
  }

  record(stage: StartupDiagnosticStage, detail: Record<string, string | number | boolean | null> = {}): void {
    if (this.disabled) {
      return;
    }
    if (STARTUP_READY_STAGES.has(stage)) {
      this.startupReadyRecorded = true;
    }
    const entry: StartupDiagnosticEntry = {
      timestamp: new Date().toISOString(),
      stage,
      detail: sanitizeDetail(detail),
    };
    try {
      this.fsImpl.mkdirSync(this.logsDir, { recursive: true });
      this.rotateIfNeeded();
      this.fsImpl.appendFileSync(this.getLogPath(), `${JSON.stringify(entry)}\n`, 'utf8');
    } catch {
      this.disabled = true;
    }
  }

  flush(): void {
    // append-only; nothing to flush
  }

  private rotateIfNeeded(): void {
    const currentPath = this.getLogPath();
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

function sanitizeDetail(
  detail: Record<string, string | number | boolean | null>,
): Record<string, string | number | boolean | null> {
  const sanitized: Record<string, string | number | boolean | null> = {};
  for (const [key, value] of Object.entries(detail)) {
    if (value === null || typeof value === 'number' || typeof value === 'boolean') {
      sanitized[key] = value;
      continue;
    }
    const text = String(value);
    if (/password|secret|token|gstin|authorization/i.test(key)) {
      sanitized[key] = '[REDACTED]';
      continue;
    }
    sanitized[key] = text.length > 500 ? `${text.slice(0, 500)}…` : text;
  }
  return sanitized;
}
