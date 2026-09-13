#!/usr/bin/env node
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { SqliteDatabase } from '../../connector/venture_connector/dist/storage/sqlite/sqlite-database.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function createSchemaV7Fixture(outputPath) {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  if (fs.existsSync(outputPath)) {
    fs.unlinkSync(outputPath);
  }

  const db = new SqliteDatabase({ databasePath: outputPath });
  db.open();
  const handle = db.getDatabase();
  handle.prepare('DELETE FROM schema_migrations WHERE version = 8').run();
  handle.exec(`
    DROP INDEX IF EXISTS idx_xml_import_attempts_reservation_scoped;
    DROP INDEX IF EXISTS idx_xml_import_attempts_reservation_no_company;
  `);
  handle.exec(`
    CREATE UNIQUE INDEX IF NOT EXISTS idx_xml_import_attempts_reservation
      ON xml_import_attempts(company_id, resource_kind, content_fingerprint)
      WHERE reservation_status IN ('active', 'completed');
  `);
  const version = handle
    .prepare('SELECT MAX(version) AS version FROM schema_migrations')
    .get();
  if (version?.version !== 7) {
    throw new Error(`Expected schema version 7, got ${version?.version ?? 'none'}`);
  }
  db.close();
  return outputPath;
}

const invokedDirectly = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (invokedDirectly) {
  const out = process.argv[2] ?? path.join(os.tmpdir(), 'venture-lifecycle-schema-v7.db');
  createSchemaV7Fixture(out);
  console.log(out);
}
