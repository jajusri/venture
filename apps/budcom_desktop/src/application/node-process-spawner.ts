import { spawn, type ChildProcess } from 'node:child_process';

import type { ManagedProcess, ProcessSpawner, SpawnSpec } from './connector-lifecycle-types.js';

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
  spawn(spec: SpawnSpec): ManagedProcess {
    const child = spawn(spec.command, [...spec.args], {
      cwd: spec.cwd,
      env: { ...process.env, ...spec.env },
      stdio: 'ignore',
      windowsHide: true,
    });
    return new NodeManagedProcess(child);
  }
}
