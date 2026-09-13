/// Local audit trail entry for security-sensitive actions.
class AuditEvent {
  const AuditEvent({
    required this.id,
    required this.type,
    required this.occurredAt,
    required this.actorId,
    this.metadata = const {},
  });

  final String id;
  final AuditEventType type;
  final DateTime occurredAt;
  final String actorId;
  final Map<String, String> metadata;
}

enum AuditEventType {
  login,
  sync,
  export,
  share,
  settingsChange,
  pairing,
}
