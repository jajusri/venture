import 'package:venture_core/venture_core.dart';

/// Lightweight service locator for Milestone 0. Replace with injectable/get_it in later milestones.
class ServiceLocator {
  static late FeatureFlagRegistry featureFlags;
  static late DomainEventBus eventBus;
  static late CapabilityEvaluator capabilityEvaluator;

  static Future<void> init() async {
    featureFlags = FeatureFlagRegistry();
    eventBus = InMemoryDomainEventBus();
    capabilityEvaluator = OwnerCapabilityEvaluator();
  }
}
