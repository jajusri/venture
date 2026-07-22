/// Connection state surfaced on the Budcom home screen.
enum BudcomConnectionState {
  live,
  refreshing,
  cached,
  stale,
  error,
  unpaired,
}

extension BudcomConnectionStateLabel on BudcomConnectionState {
  String get label => switch (this) {
        BudcomConnectionState.live => 'Live',
        BudcomConnectionState.refreshing => 'Refreshing',
        BudcomConnectionState.cached => 'Offline cache',
        BudcomConnectionState.stale => 'Stale cache',
        BudcomConnectionState.error => 'Error',
        BudcomConnectionState.unpaired => 'Setup required',
      };
}
