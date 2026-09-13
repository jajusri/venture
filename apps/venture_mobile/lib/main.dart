import 'package:flutter/material.dart';

import 'app/venture_app.dart';
import 'core/di/service_locator.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await ServiceLocator.init();
  runApp(const VentureApp());
}
