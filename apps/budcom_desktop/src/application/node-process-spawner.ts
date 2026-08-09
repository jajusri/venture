import { spawn, type ChildProcess } from 'node:child_process';

import type { ManagedProcess, ProcessDiagnostic, ProcessSpawner, SpawnSpec } from './connector-lifecycle-types.js';
import { sanitizePersistentLogText } from './persistent-log-text.js';

export interface NodeProcessSpawnerOptions {
  readonly onDiagnostic?: (diagnostic: ProcessDiagnostic) => void;
  readonly maxDiagnosticBytes?: number;
}

class NodeManagedProcess implements ManagedProcess {
  readonly pid: number;
  private readonly child: ChildProcess;

  constructor(child: ChildProcess) {
    if (!child.pid) {
      throw new Error('Failed to spawn connector process.');
    }
    this.child = child;
    this.pid = child.pid;
  }

  async kill(signal: NodeJS.Signals = 'SIGTERM'): Promise<void> {
    await new Promise<void>((resolve, reject) => {
      this.child.once('error', reject);
      this.child.kill(signal);
      this.child.once('exit', () => resolve());
    });
  }

  onExit(listener: (code: number | null, signal: NodeJS.Signals | null) => void): void {
    this.child.once('exit', (code, signal) => {
      listener(code, signal);
    });
  }
}

export class NodeProcessSpawner implements ProcessSpawner {
  constructor(private readonly options: NodeProcessSpawnerOptions = {}) {}

  spawn(spec: SpawnSpec): ManagedProcess {
    const child = spawn(spec.command, [...spec.args], {
      cwd: spec.cwd,
      env: spec.env ?? {},
      stdio: ['ignore', 'ignore', 'pipe'],
      windowsHide: true,
      shell: false,
    });
    const maxBytes = this.options.maxDiagnosticBytes ?? 8_192;
    let observedBytes = 0;
    child.stderr?.on('data', (chunk: Buffer | string) => {
      if (observedBytes >= maxBytes) return;
      const raw = String(chunk);
      const remaining = maxBytes - observedBytes;
      const bounded = Buffer.from(raw, 'utf8').subarray(0, remaining).toString('utf8');
      observedBytes += Buffer.byteLength(bounded, 'utf8');
      const text = sanitizePersistentLogText(bounded, 2_000).trim();
      if (text) this.options.onDiagnostic?.({ stream: 'stderr', text });
    });
    return new NodeManagedProcess(child);
  }
}
