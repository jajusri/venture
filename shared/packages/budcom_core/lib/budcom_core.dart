/// Budcom core domain layer — entities, value objects, events, capabilities.
library budcom_core;

export 'src/capabilities/capability.dart';
export 'src/entities/audit_event.dart';
export 'src/entities/company.dart';
export 'src/entities/connection_profile.dart';
export 'src/entities/document_export.dart';
export 'src/entities/ledger.dart';
export 'src/entities/ledger_transaction.dart';
export 'src/entities/money_amount.dart';
export 'src/entities/sync_checkpoint.dart';
export 'src/entities/voucher.dart';
export 'src/events/domain_event.dart';
export 'src/events/domain_event_bus.dart';
export 'src/events/event_types.dart';
export 'src/feature_flags/feature_flag.dart';
export 'src/feature_flags/feature_flag_registry.dart';
export 'src/value_objects/debit_credit.dart';
export 'src/value_objects/money.dart';
export 'src/value_objects/schema_version.dart';
