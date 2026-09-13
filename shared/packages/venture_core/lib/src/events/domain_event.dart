/// Base type for domain events consumed by future automation modules.
abstract class DomainEvent {
  const DomainEvent({
    required this.name,
    required this.occurredAt,
    this.correlationId,
  });

  final String name;
  final DateTime occurredAt;
  final String? correlationId;

  Map<String, Object?> toPayload();
}
