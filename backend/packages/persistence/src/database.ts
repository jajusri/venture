export interface QueryResult<Row> { readonly rows: readonly Row[]; readonly rowCount: number }
export interface DatabaseSession {
  query<Row extends Record<string, unknown>>(sql: string, parameters?: readonly unknown[]): Promise<QueryResult<Row>>;
}
export interface Database extends DatabaseSession {
  transaction<T>(work: (session: DatabaseSession) => Promise<T>): Promise<T>;
  close(): Promise<void>;
}
