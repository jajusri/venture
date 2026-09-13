/// Record of a generated or shared document.
class DocumentExport {
  const DocumentExport({
    required this.id,
    required this.type,
    required this.generatedAt,
    required this.fileName,
    this.sharedAt,
  });

  final String id;
  final DocumentExportType type;
  final DateTime generatedAt;
  final String fileName;
  final DateTime? sharedAt;
}

enum DocumentExportType {
  ledgerStatement,
  voucherCopy,
  ledgerBalanceSummary,
  diagnosticReport,
}
