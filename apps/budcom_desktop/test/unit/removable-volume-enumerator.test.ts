import { describe, expect, it } from 'vitest';

import { FakeRemovableVolumeEnumerator } from '../../src/application/private-storage/removable-volume-enumerator.js';

describe('FakeRemovableVolumeEnumerator', () => {
  it('lists whatever volumes are configured', async () => {
    const enumerator = new FakeRemovableVolumeEnumerator([
      { driveLetter: 'E:\\', label: 'USB1', fileSystem: 'NTFS', sizeBytes: 100, freeBytes: 50 },
    ]);
    const volumes = await enumerator.listRemovableVolumes();
    expect(volumes).toHaveLength(1);
    expect(volumes[0]?.driveLetter).toBe('E:\\');
  });

  it('volumeExists is case-insensitive and tolerant of a missing trailing backslash', async () => {
    const enumerator = new FakeRemovableVolumeEnumerator([
      { driveLetter: 'E:\\', label: null, fileSystem: null, sizeBytes: null, freeBytes: null },
    ]);
    expect(await enumerator.volumeExists('e:')).toBe(true);
    expect(await enumerator.volumeExists('F:')).toBe(false);
  });

  it('reports no volumes when none are configured — the "nothing removable attached" baseline', async () => {
    const enumerator = new FakeRemovableVolumeEnumerator();
    expect(await enumerator.listRemovableVolumes()).toEqual([]);
  });
});
