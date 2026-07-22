import 'package:flutter/material.dart';

import '../core/di/service_locator.dart';
import '../presentation/shell/home_shell.dart';

class BudcomApp extends StatelessWidget {
  const BudcomApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Budcom',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1B5E20)),
        useMaterial3: true,
      ),
      home: HomeShell(
        featureFlags: ServiceLocator.featureFlags,
        eventBus: ServiceLocator.eventBus,
      ),
    );
  }
}
