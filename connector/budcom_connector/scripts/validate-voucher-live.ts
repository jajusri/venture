import path from 'node:path';

import { registerServices, startApplication, stopApplication } from '../src/bootstrap/register-services.js';
import { ServiceTokens } from '../src/core/tokens.js';
import type { VoucherSnapshotSyncService } from '../src/services/voucher/voucher-application.interface.js';

const args = parseArgs(process.argv.slice(2));
const required = ['company', 'from', 'to', 'database-path'] as const;
const missing = required.filter((name) => !args.get(name)?.trim());
if (missing.length > 0 || !args.has('authorize-read-only-tally')) {
  console.error(
    'Live Voucher validation requires --company, --from, --to, --database-path, '
    + 'and --authorize-read-only-tally.',
  );
  process.exitCode = 2;
} else {
  const context = registerServices({
    env: 'production',
    host: '127.0.0.1',
    databasePath: path.resolve(args.get('database-path') as string),
  });
  try {
    await startApplication(context);
    const service = context.container.resolve<VoucherSnapshotSyncService>(
      ServiceTokens.VoucherSynchronization,
    );
    const summary = await service.synchronize(
      {
        companyId: args.get('company') as string,
        dateFrom: args.get('from') as string,
        dateTo: args.get('to') as string,
      },
      { onProgress: (progress) => console.error(JSON.stringify({ progress })) },
      { requested: false },
    );
    console.log(JSON.stringify(summary, null, 2));
    if (summary.outcome !== 'completed' && summary.outcome !== 'already_current') {
      process.exitCode = 1;
    }
  } finally {
    await stopApplication(context);
  }
}

function parseArgs(values: readonly string[]): Map<string, string> {
  const parsed = new Map<string, string>();
  for (let index = 0; index < values.length; index += 1) {
    const token = values[index];
    if (!token?.startsWith('--')) continue;
    const name = token.slice(2);
    const next = values[index + 1];
    if (!next || next.startsWith('--')) {
      parsed.set(name, 'true');
      continue;
    }
    parsed.set(name, next);
    index += 1;
  }
  return parsed;
}
