/**
 * Lightweight semaphore pool limiting concurrent in-flight requests to Tally.
 * Tally serializes heavy exports; this prevents connector self-DOS while allowing
 * ping + discovery overlap.
 */
export class ConnectionPool {
  private active = 0;
  private waiting: Array<() => void> = [];

  constructor(private readonly maxConnections: number) {}

  get activeConnections(): number {
    return this.active;
  }

  get waitingRequests(): number {
    return this.waiting.length;
  }

  async acquire(): Promise<() => void> {
    if (this.active < this.maxConnections) {
      this.active += 1;
      return () => this.release();
    }

    await new Promise<void>((resolve) => {
      this.waiting.push(resolve);
    });

    this.active += 1;
    return () => this.release();
  }

  private release(): void {
    this.active = Math.max(0, this.active - 1);
    const next = this.waiting.shift();
    if (next) {
      next();
    }
  }

  async run<T>(task: () => Promise<T>): Promise<T> {
    const release = await this.acquire();
    try {
      return await task();
    } finally {
      release();
    }
  }
}
