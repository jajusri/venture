import { Pool, type PoolClient, type QueryResultRow } from 'pg';
import type { Database, DatabaseSession, QueryResult } from './database.js';

function session(client: Pool | PoolClient): DatabaseSession {
  return { async query<Row extends QueryResultRow>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> {
    const result = await client.query<Row>(sql, [...parameters]);
    return { rows: result.rows, rowCount: result.rowCount ?? 0 };
  } };
}

export class PostgresDatabase implements Database {
  private readonly pool: Pool;
  constructor(connectionString: string, max: number) { this.pool = new Pool({ connectionString, max }); }
  query<Row extends QueryResultRow>(sql: string, parameters: readonly unknown[] = []): Promise<QueryResult<Row>> { return session(this.pool).query<Row>(sql, parameters); }
  async transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const value = await work(session(client));
      await client.query('COMMIT');
      return value;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally { client.release(); }
  }
  async close(): Promise<void> { await this.pool.end(); }
}
