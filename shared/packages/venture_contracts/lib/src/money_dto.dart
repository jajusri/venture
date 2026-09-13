import 'package:venture_core/venture_core.dart';

/// Wire-format money object included in every connector response.
class MoneyDto {
  const MoneyDto({
    required this.amount,
    required this.currencyCode,
    required this.side,
  });

  final double amount;
  final String currencyCode;
  final String side;

  factory MoneyDto.fromJson(Map<String, dynamic> json) {
    return MoneyDto(
      amount: (json['amount'] as num).toDouble(),
      currencyCode: json['currencyCode'] as String,
      side: json['side'] as String,
    );
  }

  Map<String, dynamic> toJson() => {
        'amount': amount,
        'currencyCode': currencyCode,
        'side': side,
      };

  Money toDomain() {
    final debitCredit = side == 'Dr' ? DebitCredit.debit : DebitCredit.credit;
    return Money(amount: amount, currencyCode: currencyCode, side: debitCredit);
  }

  factory MoneyDto.fromDomain(Money money) {
    return MoneyDto(
      amount: money.amount,
      currencyCode: money.currencyCode,
      side: money.side.label,
    );
  }
}
