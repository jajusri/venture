import { execFile } from 'node:child_process';

export interface RemovableVolumeInfo {
  /** e.g. "E:\\" — always drive-letter-root form, trailing backslash included. */
  readonly driveLetter: string;
  readonly label: string | null;
  readonly fileSystem: string | null;
  readonly sizeBytes: number | null;
  readonly freeBytes: number | null;
}

/** Narrow seam over "ask Windows which removable volumes exist" so tests never shell out. */
export interface RemovableVolumeEnumerator {
  listRemovableVolumes(): Promise<readonly RemovableVolumeInfo[]>;
  /** Cheap existence probe for one specific drive letter, used by the hot-removal poll — avoids re-enumerating every volume on every tick. */
  volumeExists(driveLetter: string): Promise<boolean>;
}

const QUERY_TIMEOUT_MS = 5_000;

/**
 * DriveType 2 = Removable in Win32_Volume (matches USB flash/SD readers; excludes fixed disks,
 * network drives, CD-ROM). Deliberately narrower than Get-Volume's own DriveType enum names,
 * which vary across PowerShell/CIM versions — the numeric WMI constant is stable.
 */
const LIST_VOLUMES_SCRIPT = `
$ErrorActionPreference = 'SilentlyContinue'
$volumes = Get-CimInstance -ClassName Win32_Volume -Filter "DriveType = 2" -ErrorAction SilentlyContinue
$items = @()
foreach ($volume in $volumes) {
  $obj = [ordered]@{
    driveLetter = [string]$volume.DriveLetter
    label = [string]$volume.Label
    fileSystem = [string]$volume.FileSystem
    sizeBytes = $volume.Capacity
    freeBytes = $volume.FreeSpace
  }
  $items += ,$obj
}
Write-Output ('[' + (($items | ForEach-Object { $_ | ConvertTo-Json -Compress }) -join ',') + ']')
`;

interface RawJsonVolume {
  readonly driveLetter?: string;
  readonly label?: string;
  readonly fileSystem?: string;
  readonly sizeBytes?: number;
  readonly freeBytes?: number;
}

function toNullableString(value: string | undefined): string | null {
  return value === undefined || value === '' ? null : value;
}

function toNullableNumber(value: number | undefined): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function normalizeDriveLetter(raw: string): string {
  const trimmed = raw.trim().replace(/\\+$/, '');
  return `${trimmed}\\`;
}

function mapRawVolume(raw: RawJsonVolume): RemovableVolumeInfo | null {
  if (!raw.driveLetter) return null;
  return {
    driveLetter: normalizeDriveLetter(raw.driveLetter),
    label: toNullableString(raw.label),
    fileSystem: toNullableString(raw.fileSystem),
    sizeBytes: toNullableNumber(raw.sizeBytes),
    freeBytes: toNullableNumber(raw.freeBytes),
  };
}

export interface PowerShellRemovableVolumeEnumeratorOptions {
  readonly timeoutMs?: number;
}

/** Real Windows implementation — shells a single PowerShell script per query, bounded and timeout-protected. */
export class PowerShellRemovableVolumeEnumerator implements RemovableVolumeEnumerator {
  private readonly timeoutMs: number;

  constructor(options: PowerShellRemovableVolumeEnumeratorOptions = {}) {
    this.timeoutMs = options.timeoutMs ?? QUERY_TIMEOUT_MS;
  }

  async listRemovableVolumes(): Promise<readonly RemovableVolumeInfo[]> {
    const encoded = Buffer.from(LIST_VOLUMES_SCRIPT, 'utf16le').toString('base64');
    const stdout = await this.runPowerShell(encoded);
    const trimmed = stdout.trim();
    if (!trimmed) return [];
    let parsed: unknown;
    try {
      parsed = JSON.parse(trimmed);
    } catch {
      return [];
    }
    if (!Array.isArray(parsed)) return [];
    const mapped: RemovableVolumeInfo[] = [];
    for (const item of parsed) {
      const volume = mapRawVolume(item as RawJsonVolume);
      if (volume) mapped.push(volume);
    }
    return mapped;
  }

  async volumeExists(driveLetter: string): Promise<boolean> {
    const normalized = normalizeDriveLetter(driveLetter);
    const volumes = await this.listRemovableVolumes();
    return volumes.some((volume) => volume.driveLetter.toUpperCase() === normalized.toUpperCase());
  }

  private runPowerShell(encodedCommand: string): Promise<string> {
    return new Promise((resolve, reject) => {
      execFile(
        'powershell.exe',
        ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', encodedCommand],
        { timeout: this.timeoutMs, windowsHide: true },
        (error, stdout) => {
          if (error) {
            reject(error);
            return;
          }
          resolve(stdout);
        },
      );
    });
  }
}

/** Deterministic test double — never touches a real drive or shells out. */
export class FakeRemovableVolumeEnumerator implements RemovableVolumeEnumerator {
  constructor(private volumes: readonly RemovableVolumeInfo[] = []) {}

  setVolumes(volumes: readonly RemovableVolumeInfo[]): void {
    this.volumes = volumes;
  }

  async listRemovableVolumes(): Promise<readonly RemovableVolumeInfo[]> {
    return this.volumes;
  }

  async volumeExists(driveLetter: string): Promise<boolean> {
    const normalized = normalizeDriveLetter(driveLetter);
    return this.volumes.some((volume) => volume.driveLetter.toUpperCase() === normalized.toUpperCase());
  }
}
