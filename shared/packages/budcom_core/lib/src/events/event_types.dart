import 'package:budcom_core/src/events/domain_event.dart';

/// Well-known event names emitted by the application layer.
class EventTypes {
  static const ledgerViewed = 'LedgerViewed';
  static const voucherViewed = 'VoucherViewed';
  static const documentGenerated = 'DocumentGenerated';
  static const documentShared = 'DocumentShared';
  static const syncCompleted = 'SyncCompleted';
  static const connectionFailed = 'ConnectionFailed';
}

class LedgerViewedEvent extends DomainEvent {
  LedgerViewedEvent({
    required this.ledgerId,
    required this.companyId,
    DateTime? occurredAt,
    super.correlationId,
  }) : super(name: EventTypes.ledgerViewed, occurredAt: occurredAt ?? DateTime.now().toUtc());

  final String ledgerId;
  final String companyId;

  @override
  Map<String, Object?> toPayload() => {
        'ledgerId': ledgerId,
        'companyId': companyId,
      };
}

class ConnectionFailedEvent extends DomainEvent {
  ConnectionFailedEvent({
    required this.errorCode,
    required this.userMessage,
    DateTime? occurredAt,
    super.correlationId,
  }) : super(name: EventTypes.connectionFailed, occurredAt: occurredAt ?? DateTime.now().toUtc());

  final String errorCode;
  final String userMessage;

  @override
  Map<String, Object?> toPayload() => {
        'errorCode': errorCode,
        'userMessage': userMessage,
      };
}

