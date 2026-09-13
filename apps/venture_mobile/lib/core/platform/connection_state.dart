/// Connection state surfaced on the Venture home screen.
enum VentureConnectionState {
  live,
  refreshing,
  cached,
  stale,
  error,
  unpaired,
}

extension VentureConnectionStateLabel on VentureConnectionState {
  String get label => switch (this) {
        VentureConnectionState.live => 'Live',
        VentureConnectionState.refreshing => 'Refreshing',
        VentureConnectionState.cached => 'Offline cache',
        VentureConnectionState.stale => 'Stale cache',
        VentureConnectionState.error => 'Error',
        VentureConnectionState.unpaired => 'Setup required',
      };
}
