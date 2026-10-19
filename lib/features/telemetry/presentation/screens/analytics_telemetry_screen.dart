import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/nothing_theme.dart';
import '../../../../core/theme/theme_settings_provider.dart';
import '../../../../core/widgets/frosted_glass_card.dart';
import 'package:tiqo/features/tasks/presentation/controllers/task_controller.dart';
import 'package:tiqo/features/tasks/models/task_model.dart';

class AnalyticsTelemetryScreen extends ConsumerStatefulWidget {
  const AnalyticsTelemetryScreen({super.key});

  @override
  ConsumerState<AnalyticsTelemetryScreen> createState() => _AnalyticsTelemetryScreenState();
}

class _AnalyticsTelemetryScreenState extends ConsumerState<AnalyticsTelemetryScreen> {
  int _selectedTimeframe = 1; // 0: DAY, 1: WEEK, 2: MONTH
  late DateTime _analyticsDate;

  @override
  void initState() {
    super.initState();
    _analyticsDate = DateTime.now();
  }

  @override
  Widget build(BuildContext context) {
    final allTasks = ref.watch(taskListProvider);
    final selectedDate = _analyticsDate;
    final categories = ref.watch(categoryListProvider);

    // Dynamic calculations based on real tasks for the selected timeframe
    int totalCount = 0;
    int completedCount = 0;
    double totalFocusHours = 0.0;

    if (_selectedTimeframe == 0) {
      final dayTasks = allTasks.where((t) => t.matchesDate(selectedDate)).toList();
      totalCount = dayTasks.length;
      for (final t in dayTasks) {
        if (t.isCompletedOn(selectedDate)) {
          completedCount++;
          totalFocusHours += t.durationHours;
        }
      }
    } else if (_selectedTimeframe == 1) {
      final startOfWeek = DateTime(selectedDate.year, selectedDate.month, selectedDate.day)
          .subtract(Duration(days: selectedDate.weekday - 1));
      for (int i = 0; i < 7; i++) {
        final d = startOfWeek.add(Duration(days: i));
        final dayTasks = allTasks.where((t) => t.matchesDate(d)).toList();
        totalCount += dayTasks.length;
        for (final t in dayTasks) {
          if (t.isCompletedOn(d)) {
            completedCount++;
            totalFocusHours += t.durationHours;
          }
        }
      }
    } else {
      final daysInMonth = DateUtils.getDaysInMonth(selectedDate.year, selectedDate.month);
      for (int day = 1; day <= daysInMonth; day++) {
        final d = DateTime(selectedDate.year, selectedDate.month, day);
        final dayTasks = allTasks.where((t) => t.matchesDate(d)).toList();
        totalCount += dayTasks.length;
        for (final t in dayTasks) {
          if (t.isCompletedOn(d)) {
            completedCount++;
            totalFocusHours += t.durationHours;
          }
        }
      }
    }
    final efficiencyRate = totalCount > 0 ? ((completedCount / totalCount) * 100).round() : 0;

    final themeSettings = ref.watch(themeSettingsProvider);
    final bgColor = NothingColors.getBackgroundColor(themeSettings.pitchBlackOled);
    final bottomInset = MediaQuery.of(context).padding.bottom;

    return Scaffold(
      backgroundColor: bgColor,
      body: SafeArea(
        bottom: false,
        child: SingleChildScrollView(
          padding: EdgeInsets.only(left: 16.0, right: 16.0, bottom: 108 + bottomInset),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 12),
              // Header & Interactive Date Range Picker
              _buildHeader(selectedDate),
              const SizedBox(height: 16),

              // Working Timeframe Switcher Pill (DAY / WEEK / MONTH)
              _buildTimeframeSwitcher(),
              const SizedBox(height: 16),

              // Hero Cycle Efficiency Card with dynamic Dot-Matrix Bar Chart
              _buildCycleEfficiencyHero(allTasks, efficiencyRate, selectedDate, completedCount, totalCount),
              const SizedBox(height: 16),

              // Two Metric Cards Grid (Real Focus Time & Streak)
              _buildMetricsGrid(totalFocusHours, completedCount, totalCount),
              const SizedBox(height: 16),

              // Dynamic Category Protocol Distribution
              _buildCategoryDistribution(allTasks, categories),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(DateTime selectedDate) {
    String rangeLabel;
    if (_selectedTimeframe == 0) {
      rangeLabel = DateFormat('MMM dd').format(selectedDate).toUpperCase();
    } else if (_selectedTimeframe == 1) {
      final startOfWeek = selectedDate.subtract(Duration(days: selectedDate.weekday - 1));
      final endOfWeek = startOfWeek.add(const Duration(days: 6));
      rangeLabel = '${DateFormat('MMM dd').format(startOfWeek)} - ${DateFormat('dd').format(endOfWeek)}'.toUpperCase();
    } else {
      rangeLabel = DateFormat('MMMM yyyy').format(selectedDate).toUpperCase();
    }

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: NothingColors.crimson,
                boxShadow: [
                  BoxShadow(
                    color: NothingColors.crimson,
                    blurRadius: 8,
                    spreadRadius: 1,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'ANALYTICS',
              style: NothingTypography.dotMatrix(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
          ],
        ),
        const SizedBox(width: 8),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            GestureDetector(
              onTap: () {
                HapticFeedback.lightImpact();
                setState(() {
                  if (_selectedTimeframe == 0) {
                    _analyticsDate = _analyticsDate.subtract(const Duration(days: 1));
                  } else if (_selectedTimeframe == 1) {
                    _analyticsDate = _analyticsDate.subtract(const Duration(days: 7));
                  } else {
                    _analyticsDate = DateTime(_analyticsDate.year, _analyticsDate.month - 1, _analyticsDate.day);
                  }
                });
              },
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 2),
                child: Icon(Icons.chevron_left_rounded, size: 20, color: NothingColors.muted),
              ),
            ),
            // Interactive Date Range Picker
            GestureDetector(
              onTap: () async {
                HapticFeedback.selectionClick();
                final picked = await showDatePicker(
                  context: context,
                  initialDate: selectedDate,
                  firstDate: DateTime.now().subtract(const Duration(days: 365)),
                  lastDate: DateTime.now().add(const Duration(days: 365)),
                  builder: (ctx, child) => Theme(
                    data: ThemeData.dark().copyWith(
                      colorScheme: const ColorScheme.dark(
                        primary: NothingColors.crimson,
                        surface: NothingColors.surfaceContainerLow,
                      ),
                    ),
                    child: child!,
                  ),
                );
                if (picked != null) {
                  setState(() => _analyticsDate = picked);
                }
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: NothingColors.glassSurface,
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: NothingColors.glassBorderSubtle),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '[$rangeLabel]',
                      style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.pureWhite),
                    ),
                    const SizedBox(width: 2),
                    const Icon(Icons.expand_more_rounded, size: 14, color: NothingColors.muted),
                  ],
                ),
              ),
            ),
            GestureDetector(
              onTap: () {
                HapticFeedback.lightImpact();
                setState(() {
                  if (_selectedTimeframe == 0) {
                    _analyticsDate = _analyticsDate.add(const Duration(days: 1));
                  } else if (_selectedTimeframe == 1) {
                    _analyticsDate = _analyticsDate.add(const Duration(days: 7));
                  } else {
                    _analyticsDate = DateTime(_analyticsDate.year, _analyticsDate.month + 1, _analyticsDate.day);
                  }
                });
              },
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 2),
                child: Icon(Icons.chevron_right_rounded, size: 20, color: NothingColors.muted),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildTimeframeSwitcher() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: NothingColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: NothingColors.glassBorderSubtle),
      ),
      child: Row(
        children: ['DAY', 'WEEK', 'MONTH'].asMap().entries.map((entry) {
          final index = entry.key;
          final title = entry.value;
          final isSelected = _selectedTimeframe == index;

          return Expanded(
            child: GestureDetector(
              onTap: () {
                HapticFeedback.selectionClick();
                setState(() => _selectedTimeframe = index);
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                padding: const EdgeInsets.symmetric(vertical: 7),
                decoration: BoxDecoration(
                  color: isSelected ? NothingColors.crimson : Colors.transparent,
                  borderRadius: BorderRadius.circular(999),
                  boxShadow: isSelected
                      ? [
                          BoxShadow(
                            color: NothingColors.crimson.withValues(alpha: 0.4),
                            blurRadius: 10,
                          ),
                        ]
                      : null,
                ),
                child: Center(
                  child: Text(
                    title,
                    style: NothingTypography.dotMatrix(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: isSelected ? NothingColors.pureWhite : NothingColors.muted,
                      letterSpacing: 1.0,
                    ),
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildCycleEfficiencyHero(List<TaskModel> allTasks, int efficiencyRate, DateTime selectedDate, int completedCount, int totalCount) {
    return FrostedGlassCard(
      hasCrimsonGlow: true,
      backgroundColor: NothingColors.surfaceContainer,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'TASK EFFICIENCY',
                      style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted, letterSpacing: 1.2),
                    ),
                    const SizedBox(height: 4),
                    Wrap(
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 6,
                      children: [
                        Text(
                          '$efficiencyRate%',
                          style: NothingTypography.dotMatrix(
                            fontSize: 32,
                            fontWeight: FontWeight.bold,
                            letterSpacing: -1.0,
                          ),
                        ),
                        Text(
                          totalCount > 0 ? 'COMPLETED ($completedCount/$totalCount)' : 'NO TASKS',
                          style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.crimsonDim),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                decoration: BoxDecoration(
                  color: NothingColors.crimson.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(width: 5, height: 5, decoration: const BoxDecoration(shape: BoxShape.circle, color: NothingColors.crimson)),
                    const SizedBox(width: 5),
                    Text(
                      'INSIGHTS',
                      style: NothingTypography.dotMatrix(fontSize: 9, color: NothingColors.crimsonDim, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Dynamic Dot-Matrix Bar Chart based on timeframe
          SizedBox(
            height: 140,
            child: _buildDynamicBarChart(allTasks, selectedDate),
          ),
        ],
      ),
    );
  }

  Widget _buildDynamicBarChart(List<TaskModel> allTasks, DateTime selectedDate) {
    if (_selectedTimeframe == 0) {
      // DAY view: 4 quadrants of day (08:00, 12:00, 16:00, 20:00)
      final dayTasks = allTasks.where((t) => t.matchesDate(selectedDate)).toList();

      final morning = dayTasks.where((t) => t.startHour < 12 && t.isCompletedOn(selectedDate)).length;
      final noon = dayTasks.where((t) => t.startHour >= 12 && t.startHour < 16 && t.isCompletedOn(selectedDate)).length;
      final evening = dayTasks.where((t) => t.startHour >= 16 && t.startHour < 20 && t.isCompletedOn(selectedDate)).length;
      final night = dayTasks.where((t) => t.startHour >= 20 && t.isCompletedOn(selectedDate)).length;
      final counts = [morning, noon, evening, night];
      final maxVal = counts.fold<int>(0, (max, v) => v > max ? v : max);

      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          _buildBarColumn('08-12', morning > 0 ? (morning * 3).clamp(2, 10) : 1, isPeak: morning > 0 && morning == maxVal, isDimmed: morning == 0),
          _buildBarColumn('12-16', noon > 0 ? (noon * 3).clamp(2, 10) : 1, isPeak: noon > 0 && noon == maxVal, isDimmed: noon == 0),
          _buildBarColumn('16-20', evening > 0 ? (evening * 3).clamp(2, 10) : 1, isPeak: evening > 0 && evening == maxVal, isDimmed: evening == 0),
          _buildBarColumn('20-24', night > 0 ? (night * 3).clamp(2, 10) : 1, isPeak: night > 0 && night == maxVal, isDimmed: night == 0),
        ],
      );
    } else if (_selectedTimeframe == 1) {
      // WEEK view: 7 days MON to SUN
      final startOfWeek = DateTime(selectedDate.year, selectedDate.month, selectedDate.day)
          .subtract(Duration(days: selectedDate.weekday - 1));
      final weekDaysCounts = List.generate(7, (i) {
        final d = startOfWeek.add(Duration(days: i));
        return allTasks.where((t) => t.matchesDate(d) && t.isCompletedOn(d)).length;
      });

      final maxVal = weekDaysCounts.fold<int>(0, (max, v) => v > max ? v : max);

      final daysLabels = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: List.generate(7, (i) {
          final count = weekDaysCounts[i];
          final segments = count > 0 ? (count * 3).clamp(2, 10) : 1;
          final isPeak = count > 0 && count == maxVal;
          final isWeekend = i >= 5;
          return _buildBarColumn(daysLabels[i], segments, isPeak: isPeak, isDimmed: isWeekend || count == 0);
        }),
      );
    } else {
      // MONTH view: 4 dynamic weekly buckets of the selected month
      final daysInMonth = DateUtils.getDaysInMonth(selectedDate.year, selectedDate.month);
      int countForWeek(int startDay, int endDay) {
        int count = 0;
        for (int day = startDay; day <= endDay; day++) {
          final d = DateTime(selectedDate.year, selectedDate.month, day);
          count += allTasks.where((t) => t.matchesDate(d) && t.isCompletedOn(d)).length;
        }
        return count;
      }

      final w1 = countForWeek(1, 7);
      final w2 = countForWeek(8, 14);
      final w3 = countForWeek(15, 21);
      final w4 = countForWeek(22, daysInMonth);

      final counts = [w1, w2, w3, w4];
      final maxVal = counts.fold<int>(0, (max, v) => v > max ? v : max);

      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          _buildBarColumn('WEEK 1', w1 > 0 ? (w1 * 2).clamp(2, 10) : 1, isPeak: w1 > 0 && w1 == maxVal, isDimmed: w1 == 0),
          _buildBarColumn('WEEK 2', w2 > 0 ? (w2 * 2).clamp(2, 10) : 1, isPeak: w2 > 0 && w2 == maxVal, isDimmed: w2 == 0),
          _buildBarColumn('WEEK 3', w3 > 0 ? (w3 * 2).clamp(2, 10) : 1, isPeak: w3 > 0 && w3 == maxVal, isDimmed: w3 == 0),
          _buildBarColumn('WEEK 4', w4 > 0 ? (w4 * 2).clamp(2, 10) : 1, isPeak: w4 > 0 && w4 == maxVal, isDimmed: w4 == 0),
        ],
      );
    }
  }

  Widget _buildBarColumn(String label, int filledSegments, {required bool isPeak, bool isDimmed = false}) {
    const totalSegments = 10;
    return Column(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        if (isPeak) ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
            decoration: BoxDecoration(
              color: NothingColors.crimson,
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              'MAX',
              style: NothingTypography.dotMatrix(fontSize: 8, fontWeight: FontWeight.bold),
            ),
          ),
          const SizedBox(height: 4),
        ] else
          const SizedBox(height: 18),
        for (int i = totalSegments - 1; i >= 0; i--) ...[
          Container(
            width: 18,
            height: 6,
            margin: const EdgeInsets.only(bottom: 3),
            decoration: BoxDecoration(
              color: i < filledSegments
                  ? (isPeak ? NothingColors.crimson : (isDimmed ? NothingColors.pureWhite.withValues(alpha: 0.3) : NothingColors.pureWhite))
                  : NothingColors.surfaceBright.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(1),
              boxShadow: isPeak && i < filledSegments
                  ? [
                      BoxShadow(
                        color: NothingColors.crimson.withValues(alpha: 0.5),
                        blurRadius: 4,
                      ),
                    ]
                  : null,
            ),
          ),
        ],
        const SizedBox(height: 4),
        Text(
          label,
          style: NothingTypography.dotMatrix(
            fontSize: 9,
            color: isPeak ? NothingColors.crimson : NothingColors.muted,
            fontWeight: isPeak ? FontWeight.bold : FontWeight.normal,
          ),
        ),
      ],
    );
  }

  Widget _buildMetricsGrid(double focusHours, int completedCount, int totalCount) {
    final focusMins = (focusHours * 60).round();
    final focusSubtext = focusHours > 0 ? '$focusMins mins logged' : 'No completed tasks';
    final remainingCount = totalCount - completedCount;
    final completedSubtext = remainingCount > 0
        ? '$remainingCount tasks remaining'
        : (totalCount == 0 ? 'No tasks in period' : 'All tasks completed');

    return Row(
      children: [
        Expanded(
          child: FrostedGlassCard(
            borderRadius: 20,
            padding: const EdgeInsets.all(16),
            backgroundColor: NothingColors.surfaceContainerLow,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('FOCUS TIME', style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted)),
                    const Icon(Icons.timer_outlined, size: 16, color: NothingColors.muted),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  '${focusHours.toStringAsFixed(1)}h',
                  style: NothingTypography.dotMatrix(fontSize: 22, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  focusSubtext,
                  style: NothingTypography.sans(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    color: NothingColors.tertiaryCyan,
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: FrostedGlassCard(
            borderRadius: 20,
            padding: const EdgeInsets.all(16),
            backgroundColor: NothingColors.surfaceContainerLow,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('COMPLETED', style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted)),
                    const Icon(Icons.check_circle_outline_rounded, size: 16, color: NothingColors.muted),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  '$completedCount / $totalCount',
                  style: NothingTypography.dotMatrix(fontSize: 22, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  completedSubtext,
                  style: NothingTypography.sans(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    color: remainingCount > 0 ? NothingColors.crimson : NothingColors.muted,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCategoryDistribution(List<TaskModel> allTasks, List<CategoryModel> categories) {
    final total = allTasks.isNotEmpty ? allTasks.length : 1;

    return FrostedGlassCard(
      borderRadius: 20,
      padding: const EdgeInsets.all(18),
      backgroundColor: NothingColors.surfaceContainerLow,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'TASKS BY CATEGORY',
            style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted, letterSpacing: 1.2),
          ),
          const SizedBox(height: 16),
          Column(
            children: categories.take(4).map((cat) {
              final count = allTasks.where((t) => t.category == cat.name).length;
              final pct = ((count / total) * 100).round();
              final color = Color(cat.colorValue);

              return Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Container(width: 6, height: 6, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
                            const SizedBox(width: 8),
                            Text(cat.name, style: NothingTypography.dotMatrix(fontSize: 11)),
                          ],
                        ),
                        Text('$pct%', style: NothingTypography.dotMatrix(fontSize: 10, color: color)),
                      ],
                    ),
                    const SizedBox(height: 6),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(2),
                      child: LinearProgressIndicator(
                        value: (count / total).clamp(0.05, 1.0),
                        backgroundColor: NothingColors.surfaceBright.withValues(alpha: 0.3),
                        valueColor: AlwaysStoppedAnimation<Color>(color),
                        minHeight: 3,
                      ),
                    ),
                  ],
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}
