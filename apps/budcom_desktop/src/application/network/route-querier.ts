import { execFile } from 'node:child_process';

export type WindowsNetworkProfileCategory = 'Public' | 'Private' | 'DomainAuthenticated' | 'Unknown';
export type AdapterOperationalStatus = 'Up' | 'Down' | 'Unknown';

export interface RawAdapterInfo {
  readonly interfaceIndex: number;
  /** Windows adapter GUID (InterfaceGuid) — stable per physical adapter across reboots. */
  readonly adapterId: string;
  /** Friendly name (InterfaceAlias), e.g. "Wi-Fi", "Ethernet". */
  readonly adapterName: string;
  /** Driver/device description, used to detect VPN/virtual/Docker/etc. adapters. */
  readonly interfaceDescription: string;
  /** '802.3' = Ethernet, 'Native802.11' = Wi-Fi, empty for non-physical adapters. */
  readonly mediaType: string;
  readonly operationalStatus: AdapterOperationalStatus;
  readonly ipv4: string | null;
  readonly prefixLength: number | null;
  readonly gateway: string | null;
  /** Combined route + interface metric for this adapter's default route (lower wins). */
  readonly routeMetric: number | null;
  readonly profileCategory: WindowsNetworkProfileCategory;
}

/** Narrow seam over "ask Windows for the current default-route adapters" so tests never shell out. */
export interface RouteQuerier {
  queryAdapters(): Promise<readonly RawAdapterInfo[]>;
}

const QUERY_TIMEOUT_MS = 5_000;

/**
 * A single PowerShell script emitting one JSON array combining Get-NetRoute (default route +
 * metric), Get-NetAdapter (name/description/media type/status), Get-NetIPAddress (IPv4/prefix),
 * and Get-NetConnectionProfile (Public/Private/DomainAuthenticated) — everything the active-
 * network resolver needs to pick the real physical adapter without ever hardcoding an IP.
 *
 * Serializes each candidate individually and joins manually into a JSON array: ConvertTo-Json
 * silently un-wraps a single-element array into a bare object on some PowerShell versions, which
 * would corrupt parsing on a machine with exactly one default route.
 */
const ROUTE_QUERY_SCRIPT = `
$ErrorActionPreference = 'SilentlyContinue'
$routes = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Sort-Object -Property RouteMetric
$adapters = Get-NetAdapter -ErrorAction SilentlyContinue
$ipconfigs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue
$profiles = Get-NetConnectionProfile -ErrorAction SilentlyContinue
$items = @()
foreach ($route in $routes) {
  $adapter = $adapters | Where-Object { $_.InterfaceIndex -eq $route.InterfaceIndex } | Select-Object -First 1
  $ip = $ipconfigs | Where-Object { $_.InterfaceIndex -eq $route.InterfaceIndex } | Select-Object -First 1
  $prof = $profiles | Where-Object { $_.InterfaceIndex -eq $route.InterfaceIndex } | Select-Object -First 1
  $obj = [ordered]@{
    interfaceIndex = $route.InterfaceIndex
    adapterId = [string]$adapter.InterfaceGuid
    adapterName = [string]$adapter.InterfaceAlias
    interfaceDescription = [string]$adapter.InterfaceDescription
    mediaType = [string]$adapter.MediaType
    operationalStatus = [string]$adapter.Status
    ipv4 = [string]$ip.IPAddress
    prefixLength = $ip.PrefixLength
    gateway = [string]$route.NextHop
    routeMetric = ($route.RouteMetric + $adapter.InterfaceMetric)
    profileCategory = [string]$prof.NetworkCategory
  }
  $items += ,$obj
}
Write-Output ('[' + (($items | ForEach-Object { $_ | ConvertTo-Json -Compress }) -join ',') + ']')
`;

interface RawJsonAdapter {
  readonly interfaceIndex?: number;
  readonly adapterId?: string;
  readonly adapterName?: string;
  readonly interfaceDescription?: string;
  readonly mediaType?: string;
  readonly operationalStatus?: string;
  readonly ipv4?: string;
  readonly prefixLength?: number;
  readonly gateway?: string;
  readonly routeMetric?: number;
  readonly profileCategory?: string;
}

function toNullableString(value: string | undefined): string | null {
  if (value === undefined || value === '') {
    return null;
  }
  return value;
}

function toOperationalStatus(value: string | undefined): AdapterOperationalStatus {
  if (value === 'Up' || value === 'Down') {
    return value;
  }
  return 'Unknown';
}

function toProfileCategory(value: string | undefined): WindowsNetworkProfileCategory {
  if (value === 'Public' || value === 'Private' || value === 'DomainAuthenticated') {
    return value;
  }
  return 'Unknown';
}

function mapRawAdapter(raw: RawJsonAdapter): RawAdapterInfo {
  return {
    interfaceIndex: raw.interfaceIndex ?? -1,
    adapterId: raw.adapterId ?? '',
    adapterName: raw.adapterName ?? '',
    interfaceDescription: raw.interfaceDescription ?? '',
    mediaType: raw.mediaType ?? '',
    operationalStatus: toOperationalStatus(raw.operationalStatus),
    ipv4: toNullableString(raw.ipv4),
    prefixLength: raw.prefixLength ?? null,
    gateway: toNullableString(raw.gateway),
    routeMetric: raw.routeMetric ?? null,
    profileCategory: toProfileCategory(raw.profileCategory),
  };
}

export interface PowerShellRouteQuerierOptions {
  readonly timeoutMs?: number;
}

/** Real Windows implementation — shells a single PowerShell script per query. */
export class PowerShellRouteQuerier implements RouteQuerier {
  private readonly timeoutMs: number;

  constructor(options: PowerShellRouteQuerierOptions = {}) {
    this.timeoutMs = options.timeoutMs ?? QUERY_TIMEOUT_MS;
  }

  async queryAdapters(): Promise<readonly RawAdapterInfo[]> {
    const encoded = Buffer.from(ROUTE_QUERY_SCRIPT, 'utf16le').toString('base64');
    const stdout = await this.runPowerShell(encoded);
    const trimmed = stdout.trim();
    if (!trimmed) {
      return [];
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(trimmed);
    } catch {
      return [];
    }
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.map((item) => mapRawAdapter(item as RawJsonAdapter));
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

/** Deterministic test double — never touches a real NIC or shells out. */
export class FakeRouteQuerier implements RouteQuerier {
  constructor(private adapters: readonly RawAdapterInfo[] = []) {}

  setAdapters(adapters: readonly RawAdapterInfo[]): void {
    this.adapters = adapters;
  }

  async queryAdapters(): Promise<readonly RawAdapterInfo[]> {
    return this.adapters;
  }
}
