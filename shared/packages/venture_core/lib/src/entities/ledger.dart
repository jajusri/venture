import 'package:venture_core/src/value_objects/money.dart';

/// Ledger master summary.
class Ledger {
  const Ledger({
    required this.id,
    required this.companyId,
    required this.name,
    required this.normalizedName,
    required this.closingBalance,
    this.alias,
    this.group,
    this.phone,
    this.email,
  });

  final String id;
  final String companyId;
  final String name;
  final String normalizedName;
  final Money closingBalance;
  final String? alias;
  final String? group;
  final String? phone;
  final String? email;
}
