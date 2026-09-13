import 'package:venture_core/src/value_objects/schema_version.dart';

/// Connected Tally company metadata.
class Company {
  const Company({
    required this.id,
    required this.name,
    required this.financialYear,
    required this.baseCurrency,
    required this.schemaVersion,
    this.booksBeginningDate,
    this.dataFreshnessAt,
  });

  final String id;
  final String name;
  final String financialYear;
  final String baseCurrency;
  final SchemaVersion schemaVersion;
  final DateTime? booksBeginningDate;
  final DateTime? dataFreshnessAt;
}
