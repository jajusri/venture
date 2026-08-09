import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

type PairState = 'absent' | 'partial' | 'complete';

function installerMigrationDecision(
  destination: PairState,
  sources: readonly { state: PairState; identity?: string }[],
  verificationSucceeds = true,
) {
  if (destination === 'complete') return 'preserve';
  if (destination === 'partial' || sources.some((source) => source.state === 'partial')) return 'abort';
  const identities = new Set(
    sources.filter((source) => source.state === 'complete').map((source) => source.identity),
  );
  if (identities.size > 1 || !verificationSucceeds) return 'abort';
  return identities.size === 1 ? 'migrate' : 'none';
}

describe('Windows installer firewall contract', () => {
  const script = fs.readFileSync(path.resolve(process.cwd(), 'build', 'installer.nsh'), 'utf8');
  const lifecycle = fs.readFileSync(
    path.resolve(process.cwd(), '..', '..', 'scripts', 'lifecycle', 'lifecycle-gate-runner.mjs'),
    'utf8',
  );

  it('targets only the installed bundled Node and architecture-required ports', () => {
    expect(script).toContain('$INSTDIR\\resources\\node\\node.exe');
    expect(script).toContain('protocol=TCP localport=8080 remoteip=LocalSubnet');
    expect(script).toContain('protocol=TCP localport=8443 remoteip=LocalSubnet');
    expect(script).toContain('protocol=UDP localport=5353 remoteip=LocalSubnet');
    expect(script).not.toMatch(/action=allow(?![^\r\n]*program=)/);
    expect(script).not.toContain('firewall set opmode disable');
  });

  it('replaces stable-name rules on install and removes them on uninstall', () => {
    expect((script.match(/firewall delete rule name="Budcom Connector HTTP"/g) ?? [])).toHaveLength(2);
    expect((script.match(/firewall delete rule name="Budcom Connector HTTPS"/g) ?? [])).toHaveLength(2);
    expect((script.match(/firewall delete rule name="Budcom Connector mDNS"/g) ?? [])).toHaveLength(2);
    expect(script).toContain('!macro customUnInstall');
  });

  it('migrates the real legacy transport identity before install-tree replacement', () => {
    expect(script).toContain('!macro preInit');
    expect(script).toContain('!macro customInit');
    expect(script).toContain('StrCpy $td018UserAppData "$APPDATA"');
    expect(script).toContain('Push "$INSTDIR"');
    expect(script).toContain('$0\\resources\\connector\\dist\\data\\transport');
    expect(script).toContain('$td018UserAppData\\@budcom\\desktop\\connector-transport-identity');
    expect(script.indexOf('!macro preInit')).toBeLessThan(script.indexOf('!macro customInit'));
    expect(script.indexOf('!macro customInit')).toBeLessThan(script.indexOf('!macro customInstall'));
    expect(script).toContain('ReadRegStr $td018HklmInstallDir HKLM');
    expect(script).toContain('ReadRegStr $td018HkcuInstallDir HKCU');
    expect(script).toContain('Call td018ConsiderLegacyRoot');
    expect(script).toContain('Rename "$td018StageDir" "$td018PersistentDir"');
    expect(script).toContain('"$SYSDIR\\fc.exe" /B');
    expect(script).not.toContain('StdUtils.HashFile');
  });

  it('keeps preInit limited to capturing user AppData', () => {
    const preInit = script.slice(
      script.indexOf('!macro preInit'),
      script.indexOf('!macro customInit'),
    );
    expect(preInit).toContain('StrCpy $td018UserAppData "$APPDATA"');
    expect(preInit).not.toContain('CopyFiles');
    expect(preInit).not.toContain('transport-key.pem');
  });

  it('fails closed for partial, conflicting, copy, and publication states', () => {
    expect(script).toContain('td018_destination_partial:');
    expect(script).toContain('td018_consider_partial:');
    expect(script).toContain('td018_consider_conflict:');
    expect(script).toContain('td018_copy_failed:');
    expect(script).toContain('td018_publish_failed:');
    expect(script).toContain('td018_destination_changed:');
    expect(script).toContain('RMDir /r "$td018StageDir"');
    expect(script).not.toMatch(/Delete .*transport-(?:key|cert)\.pem"/);
  });

  it('never overwrites a complete persistent identity and accepts identical roots idempotently', () => {
    const destinationCheck = script.indexOf('td018_destination_key_present:');
    const sourceSearch = script.indexOf('td018_find_sources:');
    expect(destinationCheck).toBeLessThan(sourceSearch);
    expect(script).toContain('IfFileExists "$td018PersistentDir\\transport-cert.pem" td018_done');
    expect(script).toContain('$td018CandidateDir\\transport-key.pem');
    expect(script).toContain('$td018CandidateDir\\transport-cert.pem');
  });

  it('requires a real over-install assertion before any application launch', () => {
    expect(lifecycle).toContain('--execute-windows-identity-overinstall');
    expect(lifecycle).toContain('BUDCOM_LIFECYCLE_OLD_INSTALLER');
    expect(lifecycle).toContain('BUDCOM_LIFECYCLE_OLD_INSTALL_SCOPE');
    expect(lifecycle).toContain('assertPreLaunchIdentityPreserved(before, after)');
    const overinstallStart = lifecycle.indexOf('function runIdentityOverinstallPreLaunchGate');
    const overinstallBody = lifecycle.slice(overinstallStart, lifecycle.indexOf('\nasync function main'));
    expect(overinstallBody).toContain('const overinstall = runInstaller(candidate.installerPath, true)');
    expect(overinstallBody).toContain('const after = readIdentityEvidence(paths.transportIdentityDir)');
    expect(overinstallBody).not.toContain('launchDesktop(');
    expect(overinstallBody.indexOf('const after = readIdentityEvidence'))
      .toBeLessThan(overinstallBody.indexOf('assertPreLaunchIdentityPreserved(before, after)'));
  });

  it('defines fail-closed lifecycle assertions for missing, changed, and partial pairs', () => {
    expect(lifecycle).toContain('Partial transport identity detected');
    expect(lifecycle).toContain('complete pair missing');
    expect(lifecycle).toContain('identity changed');
    expect(lifecycle).toContain('certificateSha256Preserved');
    expect(lifecycle).toContain('privateKeySha256Preserved');
    expect(lifecycle).toContain('fingerprintPreserved');
  });

  it.each([
    ['complete destination', 'complete', [], true, 'preserve'],
    ['partial destination', 'partial', [], true, 'abort'],
    ['partial source', 'absent', [{ state: 'partial' }], true, 'abort'],
    ['one complete source', 'absent', [{ state: 'complete', identity: 'A' }], true, 'migrate'],
    ['same-version duplicate', 'absent', [{ state: 'complete', identity: 'A' }, { state: 'complete', identity: 'A' }], true, 'migrate'],
    ['upgrade duplicate', 'absent', [{ state: 'complete', identity: 'A' }, { state: 'absent' }], true, 'migrate'],
    ['per-user source', 'absent', [{ state: 'absent' }, { state: 'complete', identity: 'A' }], true, 'migrate'],
    ['conflicting sources', 'absent', [{ state: 'complete', identity: 'A' }, { state: 'complete', identity: 'B' }], true, 'abort'],
    ['copy/hash failure', 'absent', [{ state: 'complete', identity: 'A' }], false, 'abort'],
    ['no prior identity', 'absent', [{ state: 'absent' }], true, 'none'],
  ] as const)('%s follows the fail-closed migration decision', (_name, destination, sources, verify, expected) => {
    expect(installerMigrationDecision(destination, sources, verify)).toBe(expected);
  });
});
