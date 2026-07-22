/// Named permissions enforced in the service layer, not only the UI.
enum Capability {
  viewCompany('view_company'),
  viewLedgerMaster('view_ledger_master'),
  viewLedgerBalance('view_ledger_balance'),
  viewLedgerTransactions('view_ledger_transactions'),
  viewVoucherList('view_voucher_list'),
  viewVoucherDetail('view_voucher_detail'),
  exportPdf('export_pdf'),
  shareDocument('share_document'),
  viewConnectionDiagnostics('view_connection_diagnostics'),
  changeConnectionSettings('change_connection_settings'),
  viewSensitiveFields('view_sensitive_fields');

  const Capability(this.code);

  final String code;
}

/// Evaluates whether a principal may perform an action.
abstract class CapabilityEvaluator {
  bool has(Capability capability);
}

/// MVP 1 default: owner has all capabilities. Replace with role-based rules later.
class OwnerCapabilityEvaluator implements CapabilityEvaluator {
  @override
  bool has(Capability capability) => true;
}

/// Denies all capabilities except an explicit allow-list.
class RestrictedCapabilityEvaluator implements CapabilityEvaluator {
  RestrictedCapabilityEvaluator(this.allowed);

  final Set<Capability> allowed;

  @override
  bool has(Capability capability) => allowed.contains(capability);
}
