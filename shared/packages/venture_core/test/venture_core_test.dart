import 'package:venture_core/venture_core.dart';
import 'package:test/test.dart';

void main() {
  group('Money', () {
    test('fromSignedBalance treats negative as credit', () {
      final money = Money.fromSignedBalance(signedAmount: -1500.5, currencyCode: 'INR');
      expect(money.side, DebitCredit.credit);
      expect(money.amount, 1500.5);
      expect(money.formatted, 'Cr 1500.50');
    });

    test('fromSignedBalance treats positive as debit', () {
      final money = Money.fromSignedBalance(signedAmount: 250, currencyCode: 'INR');
      expect(money.side, DebitCredit.debit);
      expect(money.formatted, 'Dr 250.00');
    });

    test('addition preserves currency and net sign', () {
      final debit = Money.fromSignedBalance(signedAmount: 1000, currencyCode: 'INR');
      final credit = Money.fromSignedBalance(signedAmount: -400, currencyCode: 'INR');
      final total = debit + credit;
      expect(total.side, DebitCredit.debit);
      expect(total.amount, 600);
    });

    test('rejects mixed currency addition', () {
      final a = Money.fromSignedBalance(signedAmount: 100, currencyCode: 'INR');
      final b = Money.fromSignedBalance(signedAmount: 50, currencyCode: 'USD');
      expect(() => a + b, throwsArgumentError);
    });
  });

  group('SchemaVersion', () {
    test('parse and compare', () {
      final v1 = SchemaVersion.parse('1.0.0');
      final v2 = SchemaVersion.parse('1.1.0');
      expect(v2.compareTo(v1), greaterThan(0));
      expect(v1.isCompatibleWith(SchemaVersion.parse('1.2.0')), isTrue);
      expect(v1.isCompatibleWith(SchemaVersion.parse('2.0.0')), isFalse);
    });
  });

  group('CapabilityEvaluator', () {
    test('owner has all capabilities', () {
      final evaluator = OwnerCapabilityEvaluator();
      for (final capability in Capability.values) {
        expect(evaluator.has(capability), isTrue);
      }
    });

    test('restricted evaluator honors allow-list', () {
      final evaluator = RestrictedCapabilityEvaluator({
        Capability.viewLedgerMaster,
      });
      expect(evaluator.has(Capability.viewLedgerMaster), isTrue);
      expect(evaluator.has(Capability.exportPdf), isFalse);
    });
  });
}
