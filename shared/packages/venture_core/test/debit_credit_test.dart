import 'package:venture_core/venture_core.dart';
import 'package:test/test.dart';

void main() {
  test('DebitCredit.formatAmount uses Dr/Cr labels', () {
    expect(
      DebitCredit.formatAmount(99.9, DebitCredit.debit),
      'Dr 99.90',
    );
    expect(
      DebitCredit.formatAmount(99.9, DebitCredit.credit),
      'Cr 99.90',
    );
  });

  test('FeatureFlagRegistry defaults match enum', () {
    final registry = FeatureFlagRegistry();
    expect(registry.isEnabled(FeatureFlag.dashboard), isFalse);
    expect(registry.isEnabled(FeatureFlag.favourites), isFalse);
  });
}
