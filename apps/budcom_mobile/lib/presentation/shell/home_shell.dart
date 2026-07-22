import 'package:budcom_core/budcom_core.dart';
import 'package:flutter/material.dart';

import '../../core/platform/connection_state.dart';

/// Milestone 0 shell — reserves home IA without business flows.
class HomeShell extends StatelessWidget {
  const HomeShell({
    super.key,
    required this.featureFlags,
    required this.eventBus,
  });

  final FeatureFlagRegistry featureFlags;
  final DomainEventBus eventBus;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Budcom'),
        actions: [
          IconButton(
            tooltip: 'Settings',
            onPressed: () {},
            icon: const Icon(Icons.settings_outlined),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const _StatusCard(state: BudcomConnectionState.unpaired),
          const SizedBox(height: 16),
          TextField(
            decoration: InputDecoration(
              hintText: 'Search ledgers and vouchers',
              prefixIcon: const Icon(Icons.search),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            ),
            enabled: false,
          ),
          const SizedBox(height: 24),
          _ModuleGrid(featureFlags: featureFlags),
        ],
      ),
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.state});

  final BudcomConnectionState state;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Active company', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text('Not connected', style: Theme.of(context).textTheme.bodyLarge),
            const SizedBox(height: 8),
            Text('Connection: ${state.label}'),
          ],
        ),
      ),
    );
  }
}

class _ModuleGrid extends StatelessWidget {
  const _ModuleGrid({required this.featureFlags});

  final FeatureFlagRegistry featureFlags;

  @override
  Widget build(BuildContext context) {
    final modules = [
      ('Ledgers', Icons.account_balance_wallet_outlined, true),
      ('Vouchers', Icons.receipt_long_outlined, true),
      ('Search', Icons.manage_search, true),
      ('PDF & Share', Icons.picture_as_pdf_outlined, true),
      ('Diagnostics', Icons.monitor_heart_outlined, true),
      ('Dashboard', Icons.dashboard_outlined, featureFlags.isEnabled(FeatureFlag.dashboard)),
    ];

    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: [
        for (final (label, icon, enabled) in modules)
          Opacity(
            opacity: enabled ? 1 : 0.4,
            child: ActionChip(
              avatar: Icon(icon, size: 18),
              label: Text(label),
              onPressed: enabled ? () {} : null,
            ),
          ),
      ],
    );
  }
}
