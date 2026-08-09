import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Windows installer firewall contract', () => {
  const script = fs.readFileSync(path.resolve(process.cwd(), 'build', 'installer.nsh'), 'utf8');

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
    expect(script).toContain('$INSTDIR\\resources\\connector\\dist\\data\\transport\\transport-key.pem');
    expect(script).toContain('$INSTDIR\\resources\\connector\\dist\\data\\transport\\transport-cert.pem');
    expect(script).toContain('$APPDATA\\@budcom\\desktop\\connector-transport-identity');
    expect(script.indexOf('!macro preInit')).toBeLessThan(script.indexOf('!macro customInstall'));
    expect(script).toContain('transport-key.pem.migrating');
    expect(script).toContain('transport-cert.pem.migrating');
  });
});
