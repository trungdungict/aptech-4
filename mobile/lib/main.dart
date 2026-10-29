import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:aptech4_mobile/app/app_router.dart';
import 'package:aptech4_mobile/app/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: AptechMobileApp()));
}

class AptechMobileApp extends ConsumerWidget {
  const AptechMobileApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(goRouterProvider);
    return MaterialApp.router(
      title: 'Aptech-4',
      theme: buildAppTheme(),
      routerConfig: router,
    );
  }
}

/// Kept for existing smoke tests that need a minimal MaterialApp host.
class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Aptech-4 Mobile',
      theme: buildAppTheme(),
      home: const Scaffold(
        body: Center(child: Text('Aptech-4')),
      ),
    );
  }
}
