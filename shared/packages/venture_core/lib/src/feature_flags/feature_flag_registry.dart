import 'package:venture_core/src/feature_flags/feature_flag.dart';

/// Runtime registry for feature flags; defaults come from [FeatureFlag].
class FeatureFlagRegistry {
  FeatureFlagRegistry({Map<FeatureFlag, bool>? overrides})
      : _flags = {
          for (final flag in FeatureFlag.values) flag: flag.enabled,
          ...?overrides,
        };

  final Map<FeatureFlag, bool> _flags;

  bool isEnabled(FeatureFlag flag) => _flags[flag] ?? flag.enabled;

  FeatureFlagRegistry copyWith({required FeatureFlag flag, required bool enabled}) {
    return FeatureFlagRegistry(
      overrides: {..._flags, flag: enabled},
    );
  }
}
