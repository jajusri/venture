/// Local database schema version and migration contract.
abstract class SchemaMigration {
  int get version;

  Future<void> upgrade();
}

abstract class MigrationRunner {
  Future<int> getCurrentVersion();

  Future<void> migrateTo(int targetVersion);
}

/// Milestone 0 placeholder — first real migration lands in Milestone 2.
class InitialSchemaMigration implements SchemaMigration {
  @override
  int get version => 1;

  @override
  Future<void> upgrade() async {}
}
