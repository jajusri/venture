import { describe, expect, it } from 'vitest';

import { ServiceContainer } from '../../src/core/container.js';
import { ServiceTokens } from '../../src/core/tokens.js';

describe('ServiceContainer', () => {
  it('registers and resolves singletons', () => {
    const container = new ServiceContainer();
    container.registerSingleton(ServiceTokens.Config, { port: 8080 });
    expect(container.resolve(ServiceTokens.Config)).toEqual({ port: 8080 });
  });

  it('registers and resolves factories once', () => {
    const container = new ServiceContainer();
    let calls = 0;
    container.registerFactory(ServiceTokens.Logger, () => {
      calls += 1;
      return { id: calls };
    });

    const first = container.resolve(ServiceTokens.Logger);
    const second = container.resolve(ServiceTokens.Logger);
    expect(first).toBe(second);
    expect(calls).toBe(1);
  });

  it('throws when service is missing', () => {
    const container = new ServiceContainer();
    expect(() => container.resolve(ServiceTokens.Logger)).toThrow('Service not registered');
  });
});
