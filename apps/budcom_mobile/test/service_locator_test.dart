import 'package:budcom_core/budcom_core.dart';
import 'package:test/test.dart';

import 'package:budcom_mobile/core/di/service_locator.dart';

void main() {
  setUp(() async {
    await ServiceLocator.init();
  });

  test('ServiceLocator wires core platform services', () {
    expect(ServiceLocator.capabilityEvaluator.has(Capability.viewCompany), isTrue);
    expect(ServiceLocator.featureFlags.isEnabled(FeatureFlag.dashboard), isFalse);
  });
}
