import 'package:venture_core/src/value_objects/debit_credit.dart';

/// Canonical money representation used across UI, PDF, and API layers.
class Money {
  const Money({
    required this.amount,
    required this.currencyCode,
    required this.side,
  });

  /// Absolute magnitude; use [side] for Dr/Cr presentation.
  final double amount;
  final String currencyCode;
  final DebitCredit side;

  /// Creates money from a signed ledger balance convention.
  ///
  /// Negative values are treated as credit; positive as debit.
  factory Money.fromSignedBalance({
    required double signedAmount,
    required String currencyCode,
  }) {
    if (signedAmount == 0) {
      return Money(amount: 0, currencyCode: currencyCode, side: DebitCredit.debit);
    }
    final side = signedAmount < 0 ? DebitCredit.credit : DebitCredit.debit;
    return Money(amount: signedAmount.abs(), currencyCode: currencyCode, side: side);
  }

  String get formatted => DebitCredit.formatAmount(amount, side);

  Money operator +(Money other) {
    _assertSameCurrency(other);
    final thisSigned = _toSigned();
    final otherSigned = other._toSigned();
    return Money.fromSignedBalance(
      signedAmount: thisSigned + otherSigned,
      currencyCode: currencyCode,
    );
  }

  double _toSigned() => side == DebitCredit.debit ? amount : -amount;

  void _assertSameCurrency(Money other) {
    if (currencyCode != other.currencyCode) {
      throw ArgumentError('Currency mismatch: $currencyCode vs ${other.currencyCode}');
    }
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Money &&
          runtimeType == other.runtimeType &&
          amount == other.amount &&
          currencyCode == other.currencyCode &&
          side == other.side;

  @override
  int get hashCode => Object.hash(amount, currencyCode, side);

  @override
  String toString() => '$currencyCode $formatted';
}
