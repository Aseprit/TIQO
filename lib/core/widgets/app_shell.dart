import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/calendar/presentation/screens/expanded_calendar_screen.dart';
import '../../features/settings/presentation/screens/settings_config_screen.dart';
import '../../features/tasks/presentation/screens/home_screen.dart';
import '../../features/tasks/presentation/widgets/new_task_modal.dart';
import '../../features/telemetry/presentation/screens/analytics_telemetry_screen.dart';
import '../theme/nothing_theme.dart';
import '../theme/theme_settings_provider.dart';
import 'atmospheric_glow_matrix.dart';
import 'nothing_nav_bar.dart';

class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> with SingleTickerProviderStateMixin {
  int _currentIndex = 0;

  @override
  void initState() {
    super.initState();
    AtmosphericGlowEngine.start(this);
  }

  @override
  void dispose() {
    AtmosphericGlowEngine.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final themeSettings = ref.watch(themeSettingsProvider);
    final bgColor = NothingColors.getBackgroundColor(themeSettings.pitchBlackOled);

    final List<Widget> screens = [
      HomeScreen(
        onExpandCalendar: () {
          setState(() {
            _currentIndex = 1;
          });
        },
      ),
      ExpandedCalendarScreen(
        onCollapse: () {
          setState(() {
            _currentIndex = 0;
          });
        },
      ),
      const AnalyticsTelemetryScreen(),
      const SettingsConfigScreen(),
    ];

    return Scaffold(
      backgroundColor: bgColor,
      extendBody: true,
      resizeToAvoidBottomInset: false,
      body: Stack(
        fit: StackFit.expand,
        children: [
          IndexedStack(
            index: _currentIndex,
            children: screens,
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: NothingNavBar(
              currentIndex: _currentIndex,
              onTabSelected: (index) {
                setState(() {
                  _currentIndex = index;
                });
              },
              onNewTaskPressed: () {
                NewTaskModal.show(context);
              },
            ),
          ),
        ],
      ),
    );
  }
}
