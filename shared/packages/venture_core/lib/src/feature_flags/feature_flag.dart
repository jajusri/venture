/// Feature flags enable gradual module activation without architectural change.
enum FeatureFlag {
  dashboard('dashboard', enabled: false),
  favourites('favourites', enabled: false),
  multiCompany('multi_company', enabled: false),
  deltaSync('delta_sync', enabled: false),
  cloudSync('cloud_sync', enabled: false),
  aiAssistant('ai_assistant', enabled: false);

  const FeatureFlag(this.key, {required this.enabled});

  final String key;
  final bool enabled;
}
