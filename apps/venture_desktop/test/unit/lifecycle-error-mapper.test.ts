import { describe, expect, it } from 'vitest';

import {
  mapLifecycleStateLabel,
  mapLifecycleUserMessage,
  mapSpawnError,
} from '../../src/application/lifecycle-error-mapper.js';

describe('lifecycle-error-mapper', () => {
  it('maps lifecycle states to labels', () => {
    expect(mapLifecycleStateLabel('connected')).toBe('Connected');
    expect(mapLifecycleStateLabel('reconnecting')).toBe('Reconnecting');
    expect(mapLifecycleStateLabel('failed')).toBe('Failed');
  });

  it('maps known failure reasons to user-friendly messages', () => {
    expect(mapLifecycleUserMessage('EXECUTABLE_MISSING')).toContain('not found');
    expect(mapLifecycleUserMessage('PORT_IN_USE')).toContain('already in use');
  });

  it('maps spawn permission errors', () => {
    const mapped = mapSpawnError(new Error('spawn EACCES'));
    expect(mapped.reason).toBe('PERMISSION_DENIED');
  });
});
