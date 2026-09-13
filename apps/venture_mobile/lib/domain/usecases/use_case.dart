import 'package:venture_core/venture_core.dart';

/// Application use case boundary — orchestrates domain rules without UI knowledge.
abstract class UseCase<TInput, TOutput> {
  Future<TOutput> call(TInput input);
}

/// Enforces capability checks before any read operation.
mixin CapabilityGuard {
  CapabilityEvaluator get capabilityEvaluator;

  void require(Capability capability) {
    if (!capabilityEvaluator.has(capability)) {
      throw CapabilityDeniedException(capability);
    }
  }
}

class CapabilityDeniedException implements Exception {
  CapabilityDeniedException(this.capability);

  final Capability capability;

  @override
  String toString() => 'Capability denied: ${capability.code}';
}
