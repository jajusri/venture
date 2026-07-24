import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { FileLogWriter } from '../../src/application/file-log-writer.js';
import { LogService } from '../../src/application/log-service.js';

const COMPANY = 'JAJU SANITATIONS PRIVATE LIMITED';
const LEDGER = 'Sensitive Debtor Ledger';
const GSTIN = '29AABCU9603R1ZM';
const TOKEN = 'sk-live-diagnostic-leak-test-token';
const PATH = 'C:\\Users\\ContosoUser\\Documents\\budcom.db';
const XML = '<LEDGER NAME="Secret Co"><AMOUNT>999.00</AMOUNT></LEDGER>';

describe('desktop file log sanitization parity (group 5)', () => {
  it('removes sensitive content from persisted file log lines', () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'budcom-file-log-'));
    const writer = new FileLogWriter({ logsDir: tempDir });
    const logService = new LogService({ fileWriter: writer, consoleEnabled: false });

    logService.appendStructured({
      level: 'error',
      message: `Failed for ${COMPANY} ledger ${LEDGER} gst ${GSTIN} xml ${XML} path ${PATH}`,
      event: 'lifecycle_test',
      component: 'connector-lifecycle',
      metadata: {
        errorCode: TOKEN,
        attemptNumber: 1,
        connectorOwnership: 'desktop-managed',
      },
    });

    const fileContents = fs.readFileSync(writer.getLogFilePath(), 'utf8');
    const haystack = fileContents.toLowerCase();
    expect(haystack).not.toContain('jaju');
    expect(haystack).not.toContain('sensitive debtor');
    expect(haystack).not.toContain('29aabcu9603r1zm');
    expect(haystack).not.toContain('<ledger');
    expect(haystack).not.toContain('contosouser');
    expect(haystack).not.toContain('sk-live');
    expect(fileContents).not.toContain('\u0000');
  });
});
