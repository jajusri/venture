import 'package:budcom_core/src/value_objects/money.dart';

/// Single ledger posting line within a voucher or statement.
class LedgerTransaction {
  const LedgerTransaction({
    required this.id,
    required this.ledgerId,
    required this.voucherId,
    required this.date,
    required this.voucherType,
    required this.voucherNumber,
    required this.amount,
    this.narration,
  });

  final String id;
  final String ledgerId;
  final String voucherId;
  final DateTime date;
  final String voucherType;
  final String voucherNumber;
  final Money amount;
  final String? narration;
}
