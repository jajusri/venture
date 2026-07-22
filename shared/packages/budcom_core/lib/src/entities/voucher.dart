import 'package:budcom_core/src/value_objects/money.dart';

/// Voucher summary or detail header.
class Voucher {
  const Voucher({
    required this.id,
    required this.companyId,
    required this.type,
    required this.number,
    required this.date,
    required this.partyName,
    required this.totalAmount,
    this.referenceNumber,
    this.narration,
    this.isCancelled = false,
    this.dataFreshnessAt,
  });

  final String id;
  final String companyId;
  final String type;
  final String number;
  final DateTime date;
  final String partyName;
  final Money totalAmount;
  final String? referenceNumber;
  final String? narration;
  final bool isCancelled;
  final DateTime? dataFreshnessAt;
}

/// Ledger posting within a voucher detail view.
class VoucherLedgerEntry {
  const VoucherLedgerEntry({
    required this.ledgerName,
    required this.amount,
  });

  final String ledgerName;
  final Money amount;
}

/// Inventory line within a voucher detail view.
class VoucherInventoryEntry {
  const VoucherInventoryEntry({
    required this.itemName,
    required this.quantity,
    required this.unit,
    this.rate,
    this.discount,
  });

  final String itemName;
  final double quantity;
  final String unit;
  final Money? rate;
  final Money? discount;
}
