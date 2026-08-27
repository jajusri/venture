import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { migrations } from '../packages/persistence/src/migrations.js';
import { MAX_MAILBOX_PAGE_SIZE } from '../services/relay/src/application/fetch-mailbox.js';
import { PostgresRelayRepository } from '../services/relay/src/persistence/relay-repository.js';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

describe('relay SCALE-1M structural review', () => {
  it('uses partition-scoped mailbox keys and bounded page reads instead of global scans', () => {
    const sql = migrations.find((migration) => migration.name === 'relay_durable_mailbox')!.sql;
    expect(sql).toContain('PRIMARY KEY (recipient_business_id, mailbox_id, mailbox_sequence)');
    expect(sql).toContain('relay_mailbox_status_cursor_idx');
    expect(sql).not.toContain('SERIAL');
    expect(MAX_MAILBOX_PAGE_SIZE).toBeLessThanOrEqual(50);
  });

  it('keeps repository queries scoped by recipient partition and cursor bounds', () => {
    const source = readFileSync(join(root, 'services/relay/src/persistence/relay-repository.ts'), 'utf8');
    expect(source).toContain('WHERE m.recipient_business_id = $1 AND m.mailbox_id = $2');
    expect(source).toContain('mailbox_sequence > $3');
    expect(source).toContain('LIMIT $4');
    expect(source).not.toMatch(/ORDER BY mailbox_sequence ASC(?![\s\S]*recipient_business_id)/);
    expect(new PostgresRelayRepository({} as never).persist).toBeDefined();
  });

  it('avoids a single global retry worker in Android sender policy', () => {
    const policy = readFileSync(join(root, '../apps/budcom_android/app/src/main/java/com/budcom/android/feature/transaction/domain/model/RelayOutboxRetryPolicy.kt'), 'utf8');
    const dispatcher = readFileSync(join(root, '../apps/budcom_android/app/src/main/java/com/budcom/android/feature/transaction/data/relay/RelayOutboxDispatcher.kt'), 'utf8');
    expect(policy).toContain('MAX_DISPATCH_BATCH');
    expect(policy).toContain('MAX_ATTEMPTS');
    expect(dispatcher).toContain('findPending(companyId)');
    expect(dispatcher).not.toMatch(/GlobalScope|while\s*\(\s*true\s*\)/);
  });
});
