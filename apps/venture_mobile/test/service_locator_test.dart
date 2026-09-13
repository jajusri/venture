import 'package:venture_core/venture_core.dart';
import 'package:test/test.dart';

import 'package:venture_mobile/core/di/service_locator.dart';

void main() {
  setUp(() async {
    await ServiceLocator.init();
  });

  test('ServiceLocator wires core platform services', () {
    expect(ServiceLocator.capabilityEvaluator.has(Capability.viewCompany), isTrue);
    expect(ServiceLocator.featureFlags.isEnabled(FeatureFlag.dashboard), isFalse);
  });
}
