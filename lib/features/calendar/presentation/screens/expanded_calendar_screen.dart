import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/nothing_theme.dart';
import '../../../../core/theme/theme_settings_provider.dart';
import '../../../../core/widgets/frosted_glass_card.dart';
import '../../../../core/widgets/nothing_badge.dart';
import 'package:tiqo/features/tasks/presentation/controllers/task_controller.dart';
import 'package:tiqo/features/tasks/models/task_model.dart';
import 'package:tiqo/features/tasks/presentation/widgets/new_task_modal.dart';
import 'package:tiqo/features/tasks/presentation/widgets/task_detail_modal.dart';

class ExpandedCalendarScreen extends ConsumerStatefulWidget {
  final VoidCallback? onCollapse;

  const ExpandedCalendarScreen({super.key, this.onCollapse});

  @override
  ConsumerState<ExpandedCalendarScreen> createState() => _ExpandedCalendarScreenState();
}

class _ExpandedCalendarScreenState extends ConsumerState<ExpandedCalendarScreen> {
  // View mode: 0: TIMELINE (Hourly Schedule), 1: MONTH (Full Grid)
  int _viewMode = 0;
  late final ScrollController _timelineScrollController;

  @override
  void initState() {
    super.initState();
    _timelineScrollController = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final now = DateTime.now();
      ref.read(selectedDateProvider.notifier).state = DateTime(now.year, now.month, now.day);
      _scrollToCurrentTime();
    });
  }

  void _scrollToCurrentTime() {
    if (!_timelineScrollController.hasClients) return;
    final now = DateTime.now();
    const hourHeight = 64.0;
    final targetOffset = ((now.hour - 1).clamp(0, 23)) * hourHeight;
    _timelineScrollController.animateTo(
      targetOffset,
      duration: const Duration(milliseconds: 350),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  void dispose() {
    _timelineScrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final selectedDate = ref.watch(selectedDateProvider);
    final tasksForDay = ref.watch(tasksForSelectedDateProvider);
    final allTasks = ref.watch(taskListProvider);
    final themeSettings = ref.watch(themeSettingsProvider);
    final bgColor = NothingColors.getBackgroundColor(themeSettings.pitchBlackOled);
    final bottomInset = MediaQuery.of(context).padding.bottom;

    return Scaffold(
      backgroundColor: bgColor,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Column(
                children: [
                  const SizedBox(height: 12),
                  // Sticky Top Control Bar
                  _buildTopBar(selectedDate),
                  const SizedBox(height: 14),

                  // View Mode Selector: [TIMELINE // 24H] vs [MONTH GRID]
                  _buildViewModeSelector(),
                  const SizedBox(height: 14),

                  if (_viewMode == 0) ...[
                    _buildTimelineDayHeader(selectedDate),
                    const SizedBox(height: 10),
                  ],
                ],
              ),
            ),

            // Scrollable Content Area: Only schedule graph or month grid scrolls
            Expanded(
              child: _viewMode == 1
                  ? SingleChildScrollView(
                      padding: EdgeInsets.only(left: 16, right: 16, bottom: 108 + bottomInset),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildMonthCalendarCard(selectedDate, allTasks),
                          const SizedBox(height: 20),
                          _buildAgendaListForDay(selectedDate, tasksForDay),
                        ],
                      ),
                    )
                  : SingleChildScrollView(
                      controller: _timelineScrollController,
                      padding: EdgeInsets.only(left: 16, right: 16, bottom: 108 + bottomInset),
                      child: _buildTimelineScheduleGraph(selectedDate, tasksForDay),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar(DateTime selectedDate) {
    final fullDateStr = DateFormat('d MMMM yyyy').format(selectedDate).toUpperCase();

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
              'CALENDAR',
              style: NothingTypography.dotMatrix(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
          ],
        ),
        NothingBadge(
          label: fullDateStr,
          style: BadgeStyle.outline,
          fontSize: 10,
        ),
      ],
    );
  }

  Widget _buildViewModeSelector() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: NothingColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: NothingColors.glassBorderSubtle),
      ),
      child: Row(
        children: [
          Expanded(
            child: GestureDetector(
              onTap: () {
                HapticFeedback.selectionClick();
                setState(() => _viewMode = 0);
                WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToCurrentTime());
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                padding: const EdgeInsets.symmetric(vertical: 8),
                decoration: BoxDecoration(
                  color: _viewMode == 0 ? NothingColors.crimson : Colors.transparent,
                  borderRadius: BorderRadius.circular(999),
                  boxShadow: _viewMode == 0
                      ? [
                          BoxShadow(
                            color: NothingColors.crimson.withValues(alpha: 0.4),
                            blurRadius: 10,
                          ),
                        ]
                      : null,
                ),
                child: Center(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.view_timeline_outlined,
                        size: 14,
                        color: _viewMode == 0 ? NothingColors.pureWhite : NothingColors.muted,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        'TIMELINE // 24H',
                        style: NothingTypography.dotMatrix(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: _viewMode == 0 ? NothingColors.pureWhite : NothingColors.muted,
                          letterSpacing: 1.0,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: GestureDetector(
              onTap: () {
                HapticFeedback.selectionClick();
                setState(() => _viewMode = 1);
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                padding: const EdgeInsets.symmetric(vertical: 8),
                decoration: BoxDecoration(
                  color: _viewMode == 1 ? NothingColors.crimson : Colors.transparent,
                  borderRadius: BorderRadius.circular(999),
                  boxShadow: _viewMode == 1
                      ? [
                          BoxShadow(
                            color: NothingColors.crimson.withValues(alpha: 0.4),
                            blurRadius: 10,
                          ),
                        ]
                      : null,
                ),
                child: Center(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.calendar_month_outlined,
                        size: 14,
                        color: _viewMode == 1 ? NothingColors.pureWhite : NothingColors.muted,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        'MONTH GRID',
                        style: NothingTypography.dotMatrix(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: _viewMode == 1 ? NothingColors.pureWhite : NothingColors.muted,
                          letterSpacing: 1.0,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTimelineDayHeader(DateTime selectedDate) {
    final dayLabel = DateFormat('EEE, MMM d').format(selectedDate).toUpperCase();

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            GestureDetector(
              onTap: () {
                HapticFeedback.selectionClick();
                ref.read(selectedDateProvider.notifier).state = selectedDate.subtract(const Duration(days: 1));
              },
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: NothingColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: NothingColors.glassBorderSubtle),
                ),
                child: const Icon(Icons.chevron_left_rounded, size: 16, color: NothingColors.pureWhite),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              dayLabel,
              style: NothingTypography.dotMatrix(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(width: 8),
            GestureDetector(
              onTap: () {
                HapticFeedback.selectionClick();
                ref.read(selectedDateProvider.notifier).state = selectedDate.add(const Duration(days: 1));
              },
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: NothingColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: NothingColors.glassBorderSubtle),
                ),
                child: const Icon(Icons.chevron_right_rounded, size: 16, color: NothingColors.pureWhite),
              ),
            ),
          ],
        ),
        GestureDetector(
          onTap: () {
            showModalBottomSheet(
              context: context,
              isScrollControlled: true,
              backgroundColor: Colors.transparent,
              builder: (ctx) => const NewTaskModal(),
            );
          },
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: NothingColors.crimson.withValues(alpha: 0.18),
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: NothingColors.crimson.withValues(alpha: 0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.add, size: 14, color: NothingColors.crimson),
                const SizedBox(width: 4),
                Text(
                  'ADD TASK',
                  style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.crimson, fontWeight: FontWeight.bold),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildTimelineScheduleGraph(DateTime selectedDate, List<TaskModel> tasks) {
    const startHour = 0;
    const endHour = 24;
    const hourHeight = 64.0;
    const totalHours = endHour - startHour;

    const double topInset = 16.0;
    const double bottomInset = 16.0;
    final now = DateTime.now();
    final isToday = selectedDate.year == now.year &&
                    selectedDate.month == now.month &&
                    selectedDate.day == now.day;
    final currentFraction = now.hour + now.minute / 60.0;
    final nowY = topInset + (currentFraction * hourHeight);

    final scheduledTasks = tasks.where((t) => t.hasSchedule && t.startHour >= startHour && t.startHour < endHour).toList();
    final anytimeTasks = tasks.where((t) => !t.hasSchedule).toList();
    scheduledTasks.sort((a, b) => a.startFraction.compareTo(b.startFraction));

    final Map<String, ({int colIndex, int totalCols})> layoutMap = {};
    final List<List<TaskModel>> clusters = [];
    for (final task in scheduledTasks) {
      bool added = false;
      for (final cluster in clusters) {
        final clusterStart = cluster.map((t) => t.startFraction).reduce((a, b) => a < b ? a : b);
        final clusterEnd = cluster.map((t) => t.startFraction + t.durationHours).reduce((a, b) => a > b ? a : b);
        final taskStart = task.startFraction;
        final taskEnd = task.startFraction + task.durationHours;
        if (taskStart < clusterEnd && clusterStart < taskEnd) {
          cluster.add(task);
          added = true;
          break;
        }
      }
      if (!added) {
        clusters.add([task]);
      }
    }

    for (final cluster in clusters) {
      final total = cluster.length;
      for (int i = 0; i < cluster.length; i++) {
        layoutMap[cluster[i].id] = (colIndex: i, totalCols: total);
      }
    }

    return FrostedGlassCard(
      hasCrimsonGlow: true,
      borderRadius: 24,
      backgroundColor: NothingColors.surfaceContainerLow,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'HOURLY SCHEDULE',
                style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted, letterSpacing: 1.2),
              ),
              NothingBadge(
                label: '${scheduledTasks.length} SCHEDULED',
                style: BadgeStyle.normal,
                fontSize: 9,
              ),
            ],
          ),

          if (anytimeTasks.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: NothingColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: NothingColors.glassBorderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: NothingColors.pureWhite,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        'ALL-DAY // ANYTIME (${anytimeTasks.length})',
                        style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.pureWhite, letterSpacing: 1.0),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: anytimeTasks.map((t) {
                      final isDone = t.isCompletedOn(selectedDate);
                      return GestureDetector(
                        onTap: () {
                          HapticFeedback.lightImpact();
                          TaskDetailModal.show(context, t, targetDate: selectedDate);
                        },
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          decoration: BoxDecoration(
                            color: isDone ? NothingColors.surfaceContainerHigh.withValues(alpha: 0.5) : NothingColors.surfaceContainer,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isDone ? NothingColors.glassBorderSubtle : NothingColors.glassBorder,
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              GestureDetector(
                                onTap: () {
                                  HapticFeedback.lightImpact();
                                  ref.read(taskListProvider.notifier).toggleTask(t.id, targetDate: selectedDate);
                                },
                                child: Container(
                                  width: 14,
                                  height: 14,
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(3),
                                    border: Border.all(color: isDone ? NothingColors.crimson : NothingColors.muted),
                                    color: isDone ? NothingColors.crimson : Colors.transparent,
                                  ),
                                  child: isDone
                                      ? const Icon(Icons.check, size: 10, color: NothingColors.pureWhite)
                                      : null,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                t.title,
                                style: NothingTypography.sans(
                                  fontSize: 12,
                                  color: isDone ? NothingColors.muted : NothingColors.pureWhite,
                                ).copyWith(
                                  decoration: isDone ? TextDecoration.lineThrough : null,
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
          ],

          const SizedBox(height: 16),

          LayoutBuilder(
            builder: (context, constraints) {
              const timeColumnWidth = 46.0;
              const contentGap = 10.0;
              const rightPadding = 6.0;
              final availableWidth = constraints.maxWidth - (timeColumnWidth + contentGap) - rightPadding;

              return SizedBox(
                height: (totalHours * hourHeight) + topInset + bottomInset,
                child: Stack(
                  children: [
                    // Vertical subtle separator between time column and schedule grid
                    Positioned(
                      top: topInset,
                      bottom: bottomInset,
                      left: timeColumnWidth + 2,
                      width: 1,
                      child: Container(
                        color: NothingColors.glassBorderSubtle.withValues(alpha: 0.35),
                      ),
                    ),

                    // 1. Hourly Grid Lines and Centered Labels
                    for (int h = startHour; h < endHour; h++) ...[
                      Positioned(
                        top: topInset + (h - startHour) * hourHeight - 7,
                        left: 0,
                        width: timeColumnWidth,
                        child: Text(
                          '${h.toString().padLeft(2, '0')}:00',
                          textAlign: TextAlign.center,
                          style: NothingTypography.dotMatrix(
                            fontSize: 10,
                            color: NothingColors.muted.withValues(alpha: 0.7),
                          ),
                        ),
                      ),
                      Positioned(
                        top: topInset + (h - startHour) * hourHeight,
                        left: timeColumnWidth + 3,
                        right: rightPadding,
                        height: 1,
                        child: Container(
                          color: NothingColors.glassBorderSubtle.withValues(alpha: 0.4),
                        ),
                      ),
                    ],

                    // 2. Scheduled Task Blocks placed accurately with symmetric left/right margins
                    for (final task in scheduledTasks) ...[
                      Builder(
                        builder: (context) {
                          final layout = layoutMap[task.id] ?? (colIndex: 0, totalCols: 1);
                          final colWidth = availableWidth / layout.totalCols;
                          final leftOffset = (timeColumnWidth + contentGap) + (layout.colIndex * colWidth);
                          final cardWidth = colWidth - (layout.totalCols > 1 ? 6.0 : 0.0);

                          return Positioned(
                            top: topInset + (task.startFraction * hourHeight) + 2,
                            left: leftOffset,
                            width: cardWidth,
                            height: (task.durationHours * hourHeight) - 4,
                            child: GestureDetector(
                              onTap: () {
                                HapticFeedback.lightImpact();
                                TaskDetailModal.show(context, task, targetDate: selectedDate);
                              },
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                decoration: BoxDecoration(
                                  color: task.isCompletedOn(selectedDate)
                                      ? NothingColors.surfaceContainerHigh.withValues(alpha: 0.5)
                                      : (task.isHighPriority
                                          ? NothingColors.surfaceContainerHighest
                                          : NothingColors.surfaceContainer),
                                  borderRadius: BorderRadius.circular(14),
                                  border: Border.all(
                                    color: task.isHighPriority && !task.isCompletedOn(selectedDate)
                                        ? NothingColors.crimson
                                        : NothingColors.glassBorderSubtle,
                                    width: 1.2,
                                  ),
                                  boxShadow: task.isHighPriority && !task.isCompletedOn(selectedDate)
                                      ? [
                                          BoxShadow(
                                            color: NothingColors.crimson.withValues(alpha: 0.3),
                                            blurRadius: 8,
                                          ),
                                        ]
                                      : null,
                                ),
                                child: Row(
                                  children: [
                                    // Checkbox
                                    GestureDetector(
                                      onTap: () {
                                        HapticFeedback.lightImpact();
                                        ref.read(taskListProvider.notifier).toggleTask(task.id, targetDate: selectedDate);
                                      },
                                      child: Container(
                                        width: 18,
                                        height: 18,
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: task.isCompletedOn(selectedDate) ? NothingColors.crimson : Colors.transparent,
                                          border: Border.all(
                                            color: task.isCompletedOn(selectedDate) ? NothingColors.crimson : NothingColors.muted,
                                            width: 1.5,
                                          ),
                                        ),
                                        child: task.isCompletedOn(selectedDate)
                                            ? const Icon(Icons.check, size: 12, color: NothingColors.pureWhite)
                                            : null,
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        children: [
                                          Row(
                                            children: [
                                              if (task.isRoutine) ...[
                                                const Icon(Icons.repeat_rounded, size: 11, color: NothingColors.crimson),
                                                const SizedBox(width: 3),
                                              ],
                                              Expanded(
                                                child: Text(
                                                  task.title,
                                                  style: NothingTypography.sans(
                                                    fontSize: layout.totalCols > 1 ? 11 : 12,
                                                    fontWeight: FontWeight.w600,
                                                    color: task.isCompletedOn(selectedDate) ? NothingColors.muted : NothingColors.pureWhite,
                                                  ).copyWith(
                                                    decoration: task.isCompletedOn(selectedDate) ? TextDecoration.lineThrough : null,
                                                  ),
                                                  maxLines: 1,
                                                  overflow: TextOverflow.ellipsis,
                                                ),
                                              ),
                                            ],
                                          ),
                                          Builder(
                                            builder: (context) {
                                              String typeLabel = '';
                                              if (task.taskType == TaskType.routine || task.isRoutine) {
                                                typeLabel = ' • ROUTINE';
                                              } else if (task.taskType == TaskType.event) {
                                                if (task.eventRecurrence == EventRecurrence.yearly) {
                                                  typeLabel = ' • ANNUAL';
                                                } else if (task.eventRecurrence == EventRecurrence.monthly) {
                                                  typeLabel = ' • MONTHLY';
                                                } else {
                                                  typeLabel = ' • EVENT';
                                                }
                                              }
                                              return Text(
                                                '${task.formattedStartTime} - ${task.formattedEndTime}$typeLabel [${task.category}]',
                                                style: NothingTypography.dotMatrix(
                                                  fontSize: layout.totalCols > 1 ? 8 : 9,
                                                  color: task.isHighPriority ? NothingColors.crimson : NothingColors.muted,
                                                ),
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                              );
                                            },
                                          ),
                                        ],
                                      ),
                                    ),
                                    if (task.isHighPriority && !task.isCompletedOn(selectedDate) && layout.totalCols == 1)
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
                              ),
                            ),
                          );
                        },
                      ),
                    ],

                    // 3. Live "NOW" Indicator Line - aligned strictly within the 46px time column
                    if (isToday) ...[
                      Positioned(
                        top: nowY - 9,
                        left: 0,
                        width: timeColumnWidth,
                        child: Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                            decoration: BoxDecoration(
                              color: NothingColors.crimson,
                              borderRadius: BorderRadius.circular(4),
                              boxShadow: [
                                BoxShadow(
                                  color: NothingColors.crimson.withValues(alpha: 0.6),
                                  blurRadius: 8,
                                  spreadRadius: 1,
                                ),
                              ],
                            ),
                            child: Text(
                              DateFormat('HH:mm').format(now),
                              style: NothingTypography.dotMatrix(
                                fontSize: 9,
                                fontWeight: FontWeight.bold,
                                color: NothingColors.pureWhite,
                              ),
                            ),
                          ),
                        ),
                      ),
                      Positioned(
                        top: nowY - 1,
                        left: timeColumnWidth + 3,
                        right: rightPadding,
                        height: 2,
                        child: Container(
                          decoration: BoxDecoration(
                            color: NothingColors.crimson,
                            boxShadow: [
                              BoxShadow(
                                color: NothingColors.crimson.withValues(alpha: 0.8),
                                blurRadius: 6,
                                spreadRadius: 1,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildMonthCalendarCard(DateTime selectedDate, List<TaskModel> allTasks) {
    final firstDayOfMonth = DateTime(selectedDate.year, selectedDate.month, 1);
    final daysInMonth = DateUtils.getDaysInMonth(selectedDate.year, selectedDate.month);
    final startingWeekday = firstDayOfMonth.weekday; // 1: Mon, 7: Sun

    return FrostedGlassCard(
      hasCrimsonGlow: true,
      backgroundColor: NothingColors.surfaceContainer,
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          // Month header with previous / next navigation
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton(
                icon: const Icon(Icons.chevron_left_rounded, color: NothingColors.pureWhite),
                onPressed: () {
                  HapticFeedback.selectionClick();
                  ref.read(selectedDateProvider.notifier).state = DateTime(
                    selectedDate.year,
                    selectedDate.month - 1,
                    selectedDate.day.clamp(1, 28),
                  );
                },
              ),
              Text(
                DateFormat('MMMM yyyy').format(selectedDate).toUpperCase(),
                style: NothingTypography.dotMatrix(fontSize: 14, fontWeight: FontWeight.bold, letterSpacing: 1.2),
              ),
              IconButton(
                icon: const Icon(Icons.chevron_right_rounded, color: NothingColors.pureWhite),
                onPressed: () {
                  HapticFeedback.selectionClick();
                  ref.read(selectedDateProvider.notifier).state = DateTime(
                    selectedDate.year,
                    selectedDate.month + 1,
                    selectedDate.day.clamp(1, 28),
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Day of week labels
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: ['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((day) {
              return SizedBox(
                width: 32,
                child: Center(
                  child: Text(
                    day,
                    style: NothingTypography.dotMatrix(
                      fontSize: 11,
                      color: NothingColors.muted,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 8),

          // Days grid
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: 42,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 7,
              mainAxisSpacing: 6,
              crossAxisSpacing: 4,
              childAspectRatio: 1.0,
            ),
            itemBuilder: (context, index) {
              final dayOffset = index - (startingWeekday - 1);
              if (dayOffset < 0 || dayOffset >= daysInMonth) {
                return const SizedBox.shrink();
              }

              final dayNum = dayOffset + 1;
              final cellDate = DateTime(selectedDate.year, selectedDate.month, dayNum);
              final isSelected = cellDate.year == selectedDate.year &&
                                 cellDate.month == selectedDate.month &&
                                 cellDate.day == selectedDate.day;
              final isToday = cellDate.year == DateTime.now().year &&
                              cellDate.month == DateTime.now().month &&
                              cellDate.day == DateTime.now().day;

              // Check if tasks exist on this day (including routine tasks)
              final hasTasks = allTasks.any((t) => t.matchesDate(cellDate));

              return GestureDetector(
                onTap: () {
                  HapticFeedback.selectionClick();
                  ref.read(selectedDateProvider.notifier).state = cellDate;
                },
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? NothingColors.crimson
                        : (isToday ? NothingColors.surfaceContainerHighest : Colors.transparent),
                    borderRadius: BorderRadius.circular(10),
                    border: isToday && !isSelected
                        ? Border.all(color: NothingColors.crimson.withValues(alpha: 0.6))
                        : null,
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        '$dayNum',
                        style: NothingTypography.dotMatrix(
                          fontSize: 12,
                          fontWeight: isSelected || isToday ? FontWeight.bold : FontWeight.normal,
                          color: isSelected
                              ? NothingColors.pureWhite
                              : (isToday ? NothingColors.crimson : NothingColors.pureWhite),
                        ),
                      ),
                      if (hasTasks)
                        Container(
                          margin: const EdgeInsets.only(top: 2),
                          width: 4,
                          height: 4,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: isSelected ? NothingColors.pureWhite : NothingColors.crimson,
                          ),
                        ),
                    ],
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildAgendaListForDay(DateTime selectedDate, List<TaskModel> tasks) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(width: 4, height: 16, decoration: BoxDecoration(color: NothingColors.crimson, borderRadius: BorderRadius.circular(2))),
                const SizedBox(width: 8),
                Text(
                  'TASKS // ${DateFormat('MMM dd').format(selectedDate).toUpperCase()}',
                  style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                ),
              ],
            ),
            NothingBadge(
              label: '${tasks.length} ${tasks.length == 1 ? 'TASK' : 'TASKS'}',
              style: BadgeStyle.outline,
              fontSize: 10,
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (tasks.isEmpty)
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: NothingColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Center(
              child: Text(
                'NO TASKS FOR THIS DATE',
                style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted),
              ),
            ),
          )
        else
          ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: tasks.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (ctx, i) {
              final task = tasks[i];
              final isCompleted = task.isCompletedOn(selectedDate);
              return FrostedGlassCard(
                borderRadius: 16,
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                backgroundColor: NothingColors.surfaceContainerLow,
                glowPhaseOffset: i * 2.3 + (task.title.hashCode.abs() % 10) * 0.4,
                glowSeed: (i * 3.7 + (task.id.hashCode.abs() % 17)) / 3.0,
                onTap: () {
                  HapticFeedback.lightImpact();
                  NewTaskModal.show(context, taskToEdit: task);
                },
                child: Row(
                  children: [
                    GestureDetector(
                      onTap: () {
                        HapticFeedback.lightImpact();
                        ref.read(taskListProvider.notifier).toggleTask(task.id, targetDate: selectedDate);
                      },
                      child: Icon(
                        isCompleted ? Icons.check_circle_rounded : Icons.radio_button_unchecked,
                        size: 20,
                        color: isCompleted ? NothingColors.crimson : NothingColors.muted,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              if (task.isRoutine) ...[
                                const Icon(Icons.repeat_rounded, size: 12, color: NothingColors.crimson),
                                const SizedBox(width: 4),
                              ],
                              Expanded(
                                child: Text(
                                  task.title,
                                  style: NothingTypography.sans(
                                    fontSize: 13,
                                    fontWeight: FontWeight.w600,
                                    color: isCompleted ? NothingColors.muted : NothingColors.pureWhite,
                                  ).copyWith(
                                    decoration: isCompleted ? TextDecoration.lineThrough : null,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 2),
                          Row(
                            children: [
                              Text(
                                task.timeRange,
                                style: NothingTypography.dotMatrix(
                                  fontSize: 10,
                                  color: task.isHighPriority ? NothingColors.crimson : NothingColors.muted,
                                ),
                              ),
                              if (task.taskType == TaskType.routine || task.isRoutine) ...[
                                const SizedBox(width: 5),
                                Text(
                                  '• ROUTINE',
                                  style: NothingTypography.dotMatrix(
                                    fontSize: 9,
                                    color: NothingColors.crimson,
                                  ),
                                ),
                              ] else if (task.taskType == TaskType.event) ...[
                                const SizedBox(width: 5),
                                Text(
                                  task.eventRecurrence == EventRecurrence.yearly
                                      ? '• ANNUAL EVENT'
                                      : (task.eventRecurrence == EventRecurrence.monthly ? '• MONTHLY EVENT' : '• EVENT'),
                                  style: NothingTypography.dotMatrix(
                                    fontSize: 9,
                                    color: NothingColors.tertiaryCyan,
                                  ),
                                ),
                              ],
                              const SizedBox(width: 5),
                              Text(
                                '[${task.category}]',
                                style: NothingTypography.dotMatrix(
                                  fontSize: 9,
                                  color: NothingColors.muted,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    if (task.isHighPriority)
                      Container(
                        width: 7,
                        height: 7,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: NothingColors.crimson,
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
      ],
    );
  }
}
