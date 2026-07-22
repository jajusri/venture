import { describe, expect, it } from 'vitest';

import { ConnectionPool } from '../../../src/tally/transport/connection-pool.js';

describe('ConnectionPool', () => {
  it('limits concurrent executions', async () => {
    const pool = new ConnectionPool(1);
    let active = 0;
    let maxActive = 0;

    const task = async () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise((resolve) => setTimeout(resolve, 20));
      active -= 1;
    };

    await Promise.all([pool.run(task), pool.run(task)]);
    expect(maxActive).toBe(1);
  });

  it('tracks waiting requests', async () => {
    const pool = new ConnectionPool(1);
    const releaseGate: { release?: () => void } = {};
    const gate = new Promise<void>((resolve) => {
      releaseGate.release = resolve;
    });

    const first = pool.run(async () => {
      await gate;
    });
    await new Promise((resolve) => setTimeout(resolve, 5));
    expect(pool.waitingRequests).toBe(0);

    const second = pool.run(async () => 'done');
    await new Promise((resolve) => setTimeout(resolve, 5));
    expect(pool.waitingRequests).toBe(1);

    releaseGate.release?.();
    await Promise.all([first, second]);
    expect(pool.activeConnections).toBe(0);
  });
});
