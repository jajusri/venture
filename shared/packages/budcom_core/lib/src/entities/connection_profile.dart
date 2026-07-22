/// Connector host, port, and pairing state.
class ConnectionProfile {
  const ConnectionProfile({
    required this.host,
    required this.port,
    required this.isPaired,
    this.deviceId,
    this.lastSuccessfulSyncAt,
  });

  final String host;
  final int port;
  final bool isPaired;
  final String? deviceId;
  final DateTime? lastSuccessfulSyncAt;
}
