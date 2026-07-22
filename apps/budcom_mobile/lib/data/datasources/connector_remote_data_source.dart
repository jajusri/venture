import 'package:budcom_contracts/budcom_contracts.dart';

/// Remote connector client — UI and use cases depend on this interface only.
abstract class ConnectorRemoteDataSource {
  Future<HealthResponse> getHealth();
}

/// Local encrypted cache — implementation arrives in Milestone 2.
abstract class LocalCacheDataSource {
  Future<int> getSchemaVersion();
}
