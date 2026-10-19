import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'core/router/app_router.dart';
import 'core/theme/nothing_theme.dart';
import 'core/theme/theme_settings_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Set system status bar & navigation bar to true edge-to-edge for Nothing OS
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarDividerColor: Colors.transparent,
      systemNavigationBarIconBrightness: Brightness.light,
      systemNavigationBarContrastEnforced: false,
    ),
  );

  // Initialize Hive offline database
  try {
    await Hive.initFlutter();
  } catch (_) {
    // Graceful fallback for non-native environments
  }

  runApp(
    const ProviderScope(
      child: TiqoApp(),
    ),
  );
}

class TiqoApp extends ConsumerWidget {
  const TiqoApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeSettings = ref.watch(themeSettingsProvider);
    final activeTheme = NothingTheme.getTheme(pitchBlack: themeSettings.pitchBlackOled);

    return MaterialApp.router(
      title: 'TIQO',
      debugShowCheckedModeBanner: false,
      theme: activeTheme,
      darkTheme: activeTheme,
      themeMode: ThemeMode.dark,
      routerConfig: appRouter,
    );
  }
}
