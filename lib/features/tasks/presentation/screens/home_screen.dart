import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/nothing_theme.dart';
import '../../../../core/theme/theme_settings_provider.dart';
import '../../../../core/widgets/frosted_glass_card.dart';
import '../../../../core/widgets/nothing_badge.dart';
import '../../../../core/widgets/nothing_progress_ring.dart';
import '../controllers/task_controller.dart';
import '../../models/task_model.dart';
import '../widgets/task_detail_modal.dart';

class HomeScreen extends ConsumerWidget {
  final VoidCallback onExpandCalendar;

  const HomeScreen({super.key, required this.onExpandCalendar});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedDate = ref.watch(selectedDateProvider);
    final tasks = ref.watch(tasksForSelectedDateProvider);
    final stats = ref.watch(taskStatsForSelectedDateProvider);
    final pomodoroState = ref.watch(pomodoroProvider);
    final themeSettings = ref.watch(themeSettingsProvider);
    final bgColor = NothingColors.getBackgroundColor(themeSettings.pitchBlackOled);
    final bottomInset = MediaQuery.of(context).padding.bottom;

    return Scaffold(
      backgroundColor: bgColor,
      body: SafeArea(
        bottom: false,
        child: SingleChildScrollView(
          padding: EdgeInsets.only(left: 16.0, right: 16.0, top: 0, bottom: 108 + bottomInset),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 12),
              // Top System Bar & Status
              _buildTopStatusRow(context),
              const SizedBox(height: 16),

              // Brand & Interactive 7-Day Matrix Strip
              _buildBrandAndDateStrip(context, ref, selectedDate),
              const SizedBox(height: 16),

              // Active Pomodoro Card (if running or paused)
              if (pomodoroState.isRunning || pomodoroState.isPaused) ...[
                _buildActivePomodoroCard(context, ref, pomodoroState),
                const SizedBox(height: 16),
              ],

              // Hero Bento Progress Card (Smooth Animated Cycle Metrics)
              _buildHeroProgressCard(stats),
              const SizedBox(height: 20),

              // Today's Agenda Header
              _buildAgendaHeader(stats, selectedDate),
              const SizedBox(height: 12),

              // Task List with Animated Toggles and Long-Press Deletion
              _buildTaskList(tasks, ref, context, selectedDate),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopStatusRow(BuildContext context) {
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
              'OVERVIEW',
              style: NothingTypography.dotMatrix(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
          ],
        ),
        const NothingBadge(
          label: 'LOCAL VAULT',
          style: BadgeStyle.outline,
          fontSize: 10,
        ),
      ],
    );
  }

  Widget _buildBrandAndDateStrip(BuildContext context, WidgetRef ref, DateTime selectedDate) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    // Generate 7 days around today: -2 days to +4 days
    final weekDays = List.generate(7, (i) => today.add(Duration(days: i - 2)));

    return Column(
      children: [
        Row(
          children: [
            Text(
              'TIQO',
              style: NothingTypography.dotMatrix(
                fontSize: 26,
                fontWeight: FontWeight.bold,
                letterSpacing: 2.0,
              ),
            ),
            const SizedBox(width: 6),
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
                    spreadRadius: 2,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'NOTHING OS',
              style: NothingTypography.dotMatrix(
                fontSize: 11,
                color: NothingColors.muted,
                letterSpacing: 1.5,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        // Interactive 7-Day Matrix Strip
        Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: NothingColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: NothingColors.glassBorderSubtle),
          ),
          child: Row(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: weekDays.map((date) {
                      final isSelected = date.year == selectedDate.year &&
                                         date.month == selectedDate.month &&
                                         date.day == selectedDate.day;
                      final isToday = date.year == today.year &&
                                      date.month == today.month &&
                                      date.day == today.day;
                      final dayName = DateFormat('E').format(date).toUpperCase();
                      final dayNum = date.day.toString().padLeft(2, '0');

                      return GestureDetector(
                        onTap: () {
                          HapticFeedback.selectionClick();
                          ref.read(selectedDateProvider.notifier).state = date;
                        },
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          curve: Curves.easeOutCubic,
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          decoration: BoxDecoration(
                            color: isSelected
                                ? NothingColors.surfaceContainerHighest
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(16),
                            border: isSelected
                                ? Border.all(color: NothingColors.glassBorder)
                                : null,
                          ),
                          child: Column(
                            children: [
                              Text(
                                dayName,
                                style: NothingTypography.dotMatrix(
                                  fontSize: 10,
                                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                  color: isToday ? NothingColors.crimson : NothingColors.muted,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                dayNum,
                                style: NothingTypography.dotMatrix(
                                  fontSize: 13,
                                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                  color: isSelected ? NothingColors.pureWhite : NothingColors.muted,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Container(
                                width: 5,
                                height: 5,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: isToday
                                      ? NothingColors.crimson
                                      : (isSelected ? NothingColors.pureWhite : Colors.transparent),
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ),
              IconButton(
                onPressed: onExpandCalendar,
                icon: const Icon(Icons.expand_more_rounded, color: NothingColors.muted, size: 22),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildActivePomodoroCard(BuildContext context, WidgetRef ref, PomodoroState pomodoro) {
    final task = pomodoro.activeTask;
    const dotCount = 16;
    final activeDots = (pomodoro.progress * dotCount).round().clamp(0, dotCount);

    return FrostedGlassCard(
      hasCrimsonGlow: true,
      backgroundColor: const Color(0xFF140F0F),
      borderColor: NothingColors.crimson.withValues(alpha: 0.5),
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
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
                        BoxShadow(color: NothingColors.crimson, blurRadius: 8, spreadRadius: 2),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'POMODORO FOCUS',
                    style: NothingTypography.dotMatrix(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1.2,
                    ),
                  ),
                ],
              ),
              NothingBadge(
                label: pomodoro.isPaused ? 'PAUSED' : 'GLYPH LED ACTIVE',
                style: pomodoro.isPaused ? BadgeStyle.outline : BadgeStyle.normal,
                fontSize: 9,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      task?.title ?? 'Active Session',
                      style: NothingTypography.sans(fontSize: 14, fontWeight: FontWeight.bold, color: NothingColors.pureWhite),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${(pomodoro.progress * 100).round()}% REMAINING // ${task?.category ?? "CORE"}',
                      style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted),
                    ),
                  ],
                ),
              ),
              Text(
                pomodoro.formattedTime,
                style: NothingTypography.dotMatrix(fontSize: 26, fontWeight: FontWeight.bold, letterSpacing: 1.5),
              ),
            ],
          ),
          const SizedBox(height: 14),
          // LED Bar Simulator (16 Segmented Dots)
          Row(
            children: List.generate(dotCount, (index) {
              final isLit = index < activeDots;
              return Expanded(
                child: Container(
                  height: 6,
                  margin: const EdgeInsets.symmetric(horizontal: 1.5),
                  decoration: BoxDecoration(
                    color: isLit ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(2),
                    boxShadow: isLit
                        ? [BoxShadow(color: NothingColors.crimson.withValues(alpha: 0.6), blurRadius: 4)]
                        : null,
                  ),
                ),
              );
            }),
          ),
          const SizedBox(height: 14),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              TextButton(
                onPressed: () {
                  HapticFeedback.lightImpact();
                  ref.read(pomodoroProvider.notifier).cancel();
                },
                child: Text('CANCEL', style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted)),
              ),
              const SizedBox(width: 8),
              ElevatedButton.icon(
                onPressed: () {
                  HapticFeedback.mediumImpact();
                  if (pomodoro.isRunning) {
                    ref.read(pomodoroProvider.notifier).pause();
                  } else {
                    ref.read(pomodoroProvider.notifier).resume();
                  }
                },
                icon: Icon(pomodoro.isRunning ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 16),
                label: Text(pomodoro.isRunning ? 'PAUSE' : 'RESUME', style: NothingTypography.dotMatrix(fontSize: 11, fontWeight: FontWeight.bold)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: NothingColors.crimson,
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildHeroProgressCard(TaskStats stats) {
    final remainingCount = stats.total - stats.completed;

    return FrostedGlassCard(
      hasCrimsonGlow: true,
      backgroundColor: NothingColors.surfaceContainer,
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.donut_large_rounded, color: NothingColors.crimsonDim, size: 18),
                  const SizedBox(width: 8),
                  Text(
                    'TODAY\'S PROGRESS',
                    style: NothingTypography.dotMatrix(
                      fontSize: 12,
                      color: NothingColors.muted,
                      letterSpacing: 1.2,
                    ),
                  ),
                ],
              ),
              const NothingBadge(
                label: 'LIVE',
                style: BadgeStyle.normal,
                fontSize: 10,
              ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              // Smooth TweenAnimationBuilder for Progress Ring and Percentage
              TweenAnimationBuilder<double>(
                tween: Tween<double>(begin: 0.0, end: stats.percentage),
                duration: const Duration(milliseconds: 450),
                curve: Curves.easeOutCubic,
                builder: (context, value, _) {
                  final animatedPercentageInt = (value * 100).round();
                  return NothingProgressRing(
                    progress: value,
                    size: 96,
                    strokeWidth: 8,
                    centerChild: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '$animatedPercentageInt',
                          style: NothingTypography.dotMatrix(
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                            color: NothingColors.pureWhite,
                          ),
                        ),
                        Text(
                          '%',
                          style: NothingTypography.dotMatrix(
                            fontSize: 10,
                            color: NothingColors.crimson,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          'DONE',
                          style: NothingTypography.dotMatrix(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 1.0,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Container(
                          width: 6,
                          height: 6,
                          decoration: const BoxDecoration(
                            shape: BoxShape.circle,
                            color: NothingColors.crimson,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${stats.completed} of ${stats.total} tasks completed',
                      style: NothingTypography.sans(
                        fontSize: 13,
                        color: NothingColors.pureWhite,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    Text(
                      stats.total == 0
                          ? 'No tasks scheduled for this day'
                          : (remainingCount == 0
                              ? 'All tasks completed for today'
                              : '$remainingCount tasks remaining today'),
                      style: NothingTypography.sans(
                        fontSize: 11,
                        color: NothingColors.muted,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: NothingColors.surfaceContainerHigh,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 5,
                                height: 5,
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: NothingColors.crimson,
                                ),
                              ),
                              const SizedBox(width: 5),
                              Text(
                                '$remainingCount REMAINING',
                                style: NothingTypography.dotMatrix(fontSize: 10),
                              ),
                            ],
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: NothingColors.surfaceContainerHigh,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.trending_up, color: NothingColors.tertiaryCyan, size: 14),
                              const SizedBox(width: 4),
                              Text(
                                '${(stats.percentage * 100).round()}% DONE',
                                style: NothingTypography.dotMatrix(
                                  fontSize: 10,
                                  color: NothingColors.tertiaryCyan,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAgendaHeader(TaskStats stats, DateTime selectedDate) {
    final now = DateTime.now();
    final isToday = selectedDate.year == now.year &&
                    selectedDate.month == now.month &&
                    selectedDate.day == now.day;
    final title = isToday ? "TODAY'S AGENDA" : "${DateFormat('MMM dd').format(selectedDate).toUpperCase()} AGENDA";

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Container(
              width: 4,
              height: 16,
              decoration: BoxDecoration(
                color: NothingColors.crimson,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              title,
              style: NothingTypography.dotMatrix(
                fontSize: 13,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.5,
              ),
            ),
          ],
        ),
        NothingBadge(
          label: '[${stats.completed.toString().padLeft(2, '0')}/${stats.total.toString().padLeft(2, '0')}]',
          style: BadgeStyle.outline,
          fontSize: 11,
        ),
      ],
    );
  }

  Widget _buildTaskList(List<TaskModel> tasks, WidgetRef ref, BuildContext context, DateTime selectedDate) {
    if (tasks.isEmpty) {
      return Container(
        padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 20),
        decoration: BoxDecoration(
          color: NothingColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: NothingColors.glassBorderSubtle),
        ),
        child: Center(
          child: Column(
            children: [
              const Icon(Icons.calendar_today_outlined, color: NothingColors.muted, size: 28),
              const SizedBox(height: 10),
              Text(
                'NO TASKS FOR THIS DATE',
                style: NothingTypography.dotMatrix(fontSize: 12, color: NothingColors.muted),
              ),
              const SizedBox(height: 6),
              Text(
                'Tap + to schedule a task',
                style: NothingTypography.sans(fontSize: 11, color: NothingColors.muted),
              ),
            ],
          ),
        ),
      );
    }

    return ListView.separated(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: tasks.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (ctx, index) {
        final task = tasks[index];
        final isCompleted = task.isCompletedOn(selectedDate);

        return Dismissible(
          key: Key(task.id),
          direction: DismissDirection.endToStart,
          background: Container(
            alignment: Alignment.centerRight,
            padding: const EdgeInsets.only(right: 20),
            decoration: BoxDecoration(
              color: NothingColors.crimson,
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Icon(Icons.delete_forever_rounded, color: NothingColors.pureWhite),
          ),
          onDismissed: (_) {
            HapticFeedback.heavyImpact();
            ref.read(taskListProvider.notifier).deleteTask(task.id);
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                backgroundColor: NothingColors.surfaceContainerHighest,
                content: Text(
                  'TASK DELETED // ${task.title}',
                  style: NothingTypography.dotMatrix(fontSize: 11),
                ),
                duration: const Duration(seconds: 2),
              ),
            );
          },
          child: FrostedGlassCard(
            borderRadius: 18,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            backgroundColor: task.isHighPriority && !isCompleted
                ? NothingColors.surfaceContainer
                : NothingColors.surfaceContainerLow,
            borderColor: task.isHighPriority && !isCompleted
                ? NothingColors.crimson.withValues(alpha: 0.5)
                : NothingColors.glassBorderSubtle,
            glowPhaseOffset: index * 2.3 + (task.title.hashCode.abs() % 10) * 0.4,
            glowSeed: (index * 3.7 + (task.id.hashCode.abs() % 17)) / 3.0,
            onTap: () {
              HapticFeedback.lightImpact();
              TaskDetailModal.show(context, task, targetDate: selectedDate);
            },
            onLongPress: () {
              _showDeleteConfirmationDialog(context, ref, task);
            },
            child: Row(
              children: [
                // Animated Checkbox
                GestureDetector(
                  onTap: () {
                    HapticFeedback.lightImpact();
                    ref.read(taskListProvider.notifier).toggleTask(task.id, targetDate: selectedDate);
                  },
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    curve: Curves.easeOutCubic,
                    width: 26,
                    height: 26,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isCompleted
                          ? NothingColors.crimson
                          : (task.isHighPriority
                              ? NothingColors.surfaceContainerHighest
                              : NothingColors.surfaceContainerHigh),
                      boxShadow: isCompleted
                          ? [
                              BoxShadow(
                                color: NothingColors.crimson.withValues(alpha: 0.5),
                                blurRadius: 8,
                              ),
                            ]
                          : null,
                    ),
                    child: Center(
                      child: isCompleted
                          ? const Icon(Icons.check, size: 16, color: NothingColors.pureWhite)
                          : (task.isHighPriority
                              ? Container(
                                  width: 8,
                                  height: 8,
                                  decoration: const BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: NothingColors.crimson,
                                  ),
                                )
                              : null),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                // Title & Time Range
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          if (task.taskType == TaskType.routine || task.isRoutine) ...[
                            const Icon(Icons.repeat_rounded, size: 12, color: NothingColors.crimson),
                            const SizedBox(width: 4),
                          ] else if (task.taskType == TaskType.event) ...[
                            const Icon(Icons.calendar_month_outlined, size: 12, color: NothingColors.tertiaryCyan),
                            const SizedBox(width: 4),
                          ],
                          Expanded(
                            child: Text(
                              task.title,
                              style: NothingTypography.sans(
                                fontSize: 13,
                                fontWeight: FontWeight.w500,
                                color: isCompleted ? NothingColors.muted : NothingColors.pureWhite,
                              ).copyWith(
                                decoration: isCompleted ? TextDecoration.lineThrough : null,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          Text(
                            task.timeRange,
                            style: NothingTypography.dotMatrix(
                              fontSize: 10,
                              color: NothingColors.muted,
                            ),
                          ),
                          if (task.taskType == TaskType.routine || task.isRoutine) ...[
                            const SizedBox(width: 5),
                            Text('• ROUTINE', style: NothingTypography.dotMatrix(fontSize: 9, color: NothingColors.crimson)),
                          ] else if (task.taskType == TaskType.event) ...[
                            const SizedBox(width: 5),
                            Text(
                              task.eventRecurrence == EventRecurrence.yearly
                                  ? '• ANNUAL EVENT'
                                  : (task.eventRecurrence == EventRecurrence.monthly ? '• MONTHLY EVENT' : '• EVENT'),
                              style: NothingTypography.dotMatrix(fontSize: 9, color: NothingColors.tertiaryCyan),
                            ),
                          ],
                          const SizedBox(width: 5),
                          Text(
                            '[${task.category}]',
                            style: NothingTypography.dotMatrix(fontSize: 9, color: NothingColors.muted),
                          ),
                          if (task.description != null && task.description!.trim().isNotEmpty) ...[
                            const SizedBox(width: 5),
                            const Icon(Icons.notes_rounded, size: 11, color: NothingColors.muted),
                          ],
                          if (task.reminderOffsetMinutes != null) ...[
                            const SizedBox(width: 5),
                            const Icon(Icons.notifications_active_outlined, size: 10, color: NothingColors.muted),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                // Pomodoro Focus Trigger Button
                if (task.hasPomodoro && !isCompleted)
                  GestureDetector(
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      ref.read(pomodoroProvider.notifier).start(task);
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      margin: const EdgeInsets.only(right: 6),
                      decoration: BoxDecoration(
                        color: NothingColors.crimson.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: NothingColors.crimson.withValues(alpha: 0.5)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.play_arrow_rounded, size: 12, color: NothingColors.crimson),
                          const SizedBox(width: 2),
                          Text(
                            'FOCUS',
                            style: NothingTypography.dotMatrix(
                              fontSize: 9,
                              color: NothingColors.crimson,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                // Category Badge
                NothingBadge(
                  label: task.category,
                  style: BadgeStyle.normal,
                  fontSize: 10,
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showDeleteConfirmationDialog(BuildContext context, WidgetRef ref, TaskModel task) {
    HapticFeedback.heavyImpact();
    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          backgroundColor: NothingColors.surfaceContainerLowest,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: const BorderSide(color: NothingColors.glassBorderSubtle),
          ),
          title: Row(
            children: [
              const Icon(Icons.warning_amber_rounded, color: NothingColors.crimson, size: 20),
              const SizedBox(width: 8),
              Text(
                'DELETE TASK?',
                style: NothingTypography.dotMatrix(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.0,
                ),
              ),
            ],
          ),
          content: Text(
            'Are you sure you want to delete "${task.title}"?',
            style: NothingTypography.sans(fontSize: 13, color: NothingColors.muted),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text(
                'CANCEL',
                style: NothingTypography.dotMatrix(fontSize: 12, color: NothingColors.muted),
              ),
            ),
            ElevatedButton(
              onPressed: () {
                ref.read(taskListProvider.notifier).deleteTask(task.id);
                Navigator.pop(ctx);
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: NothingColors.crimson,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: Text(
                'DELETE',
                style: NothingTypography.dotMatrix(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: NothingColors.pureWhite,
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
