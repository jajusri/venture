/// GET /health response body.
class HealthResponse {
  const HealthResponse({
    required this.status,
    required this.schemaVersion,
    required this.connectorVersion,
    required this.tallyReachable,
    required this.readOnly,
  });

  final String status;
  final String schemaVersion;
  final String connectorVersion;
  final bool tallyReachable;
  final bool readOnly;

  factory HealthResponse.fromJson(Map<String, dynamic> json) {
    return HealthResponse(
      status: json['status'] as String,
      schemaVersion: json['schemaVersion'] as String,
      connectorVersion: json['connectorVersion'] as String,
      tallyReachable: json['tallyReachable'] as bool,
      readOnly: json['readOnly'] as bool,
    );
  }

  Map<String, dynamic> toJson() => {
        'status': status,
        'schemaVersion': schemaVersion,
        'connectorVersion': connectorVersion,
        'tallyReachable': tallyReachable,
        'readOnly': readOnly,
      };
}
