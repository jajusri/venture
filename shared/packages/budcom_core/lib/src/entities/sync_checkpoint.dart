/// Freshness and synchronization progress for a company cache.
class SyncCheckpoint {
  const SyncCheckpoint({
    required this.companyId,
    required this.lastSuccessfulSyncAt,
    required this.schemaVersion,
    this.isRefreshing = false,
    this.errorCode,
  });

  final String companyId;
  final DateTime? lastSuccessfulSyncAt;
  final String schemaVersion;
  final bool isRefreshing;
  final String? errorCode;
}
