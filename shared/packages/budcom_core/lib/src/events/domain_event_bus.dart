import 'dart:async';

import 'package:budcom_core/src/events/domain_event.dart';

/// Publishes domain events to subscribers without coupling modules.
abstract class DomainEventBus {
  void publish(DomainEvent event);

  Stream<DomainEvent> get stream;
}

/// In-memory bus for MVP foundation; replace with persistent/outbox later if needed.
class InMemoryDomainEventBus implements DomainEventBus {
  final _controller = StreamController<DomainEvent>.broadcast();

  @override
  void publish(DomainEvent event) {
    _controller.add(event);
  }

  @override
  Stream<DomainEvent> get stream => _controller.stream;

  Future<void> dispose() => _controller.close();
}
