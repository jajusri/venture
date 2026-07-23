import { createRequire } from 'node:module';

const nodeRequire = createRequire(import.meta.url);

type DatabaseSyncModule = typeof import('node:sqlite');

/** Runtime access to Node's built-in sqlite module without Vite transforming the import. */
export const nodeSqlite = nodeRequire('node:sqlite') as DatabaseSyncModule;

export type DatabaseSync = InstanceType<DatabaseSyncModule['DatabaseSync']>;
