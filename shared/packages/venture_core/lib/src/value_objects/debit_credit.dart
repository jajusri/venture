/// Debit or credit side of an accounting entry.
enum DebitCredit {
  debit('Dr'),
  credit('Cr');

  const DebitCredit(this.label);

  final String label;

  /// Returns the opposite side.
  DebitCredit get opposite => this == DebitCredit.debit ? DebitCredit.credit : DebitCredit.debit;

  /// Applies a signed amount according to Tally-style Dr/Cr semantics.
  ///
  /// Debit increases asset/expense balances; credit increases liability/income.
  /// For display totals, preserve the source sign and label explicitly.
  static String formatAmount(double amount, DebitCredit side) {
    final normalized = amount.abs();
    return '${side.label} ${normalized.toStringAsFixed(2)}';
  }
}
