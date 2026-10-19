import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/nothing_theme.dart';
import '../../models/task_model.dart';
import '../controllers/task_controller.dart';
import 'new_task_modal.dart';

class TaskDetailModal extends ConsumerWidget {
  final TaskModel task;
  final DateTime targetDate;

  const TaskDetailModal({
    super.key,
    required this.task,
    required this.targetDate,
  });

  static Future<void> show(BuildContext context, TaskModel task, {DateTime? targetDate}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => TaskDetailModal(
        task: task,
        targetDate: targetDate ?? DateTime.now(),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Watch real-time task state from provider in case it changes
    final allTasks = ref.watch(taskListProvider);
    final currentTask = allTasks.firstWhere((t) => t.id == task.id, orElse: () => task);
    final isCompleted = currentTask.isCompletedOn(targetDate);

    final typeLabel = currentTask.taskType == TaskType.routine
        ? 'ROUTINE'
        : (currentTask.taskType == TaskType.event
            ? (currentTask.eventRecurrence == EventRecurrence.yearly
                ? 'ANNUAL EVENT'
                : (currentTask.eventRecurrence == EventRecurrence.monthly ? 'MONTHLY EVENT' : 'EVENT'))
            : 'TASK');

    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: ClipRRect(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
          child: Container(
            color: const Color(0xEE121212),
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            child: SafeArea(
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Drag Handle
                    Center(
                      child: Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                          color: NothingColors.surfaceBright,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Top Badges & Close
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: currentTask.taskType == TaskType.event
                                    ? NothingColors.tertiaryCyan.withValues(alpha: 0.2)
                                    : (currentTask.taskType == TaskType.routine
                                        ? NothingColors.crimson.withValues(alpha: 0.2)
                                        : NothingColors.surfaceContainerHigh),
                                borderRadius: BorderRadius.circular(999),
                                border: Border.all(
                                  color: currentTask.taskType == TaskType.event
                                      ? NothingColors.tertiaryCyan
                                      : (currentTask.taskType == TaskType.routine
                                          ? NothingColors.crimson
                                          : NothingColors.glassBorderSubtle),
                                ),
                              ),
                              child: Text(
                                typeLabel,
                                style: NothingTypography.dotMatrix(
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                  color: currentTask.taskType == TaskType.event
                                      ? NothingColors.tertiaryCyan
                                      : (currentTask.taskType == TaskType.routine
                                          ? NothingColors.crimson
                                          : NothingColors.pureWhite),
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: NothingColors.surfaceContainerHigh,
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Text(
                                currentTask.category,
                                style: NothingTypography.dotMatrix(
                                  fontSize: 10,
                                  color: NothingColors.muted,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    // Title
                    Text(
                      currentTask.title,
                      style: NothingTypography.sans(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        color: NothingColors.pureWhite,
                      ).copyWith(
                        decoration: isCompleted ? TextDecoration.lineThrough : null,
                      ),
                    ),
                    const SizedBox(height: 12),

                    // Schedule & Date Info Card
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: NothingColors.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: NothingColors.glassBorderSubtle),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 32,
                            height: 32,
                            decoration: const BoxDecoration(
                              shape: BoxShape.circle,
                              color: NothingColors.surfaceContainerHigh,
                            ),
                            child: const Icon(Icons.schedule_rounded, size: 16, color: NothingColors.pureWhite),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  currentTask.hasSchedule ? currentTask.timeRange : 'ALL-DAY // ANYTIME',
                                  style: NothingTypography.dotMatrix(
                                    fontSize: 13,
                                    fontWeight: FontWeight.bold,
                                    color: NothingColors.pureWhite,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  DateFormat('EEEE, MMMM dd, yyyy').format(targetDate),
                                  style: NothingTypography.sans(
                                    fontSize: 11,
                                    color: NothingColors.muted,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),

                    // Multi-day Event Dates List (if applicable)
                    if (currentTask.taskType == TaskType.event && currentTask.eventDates.isNotEmpty) ...[
                      const SizedBox(height: 14),
                      Builder(
                        builder: (context) {
                          final uniqueDates = currentTask.eventDates.toSet().toList()..sort();
                          final count = uniqueDates.length;
                          return Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: NothingColors.surfaceContainerLow,
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(color: NothingColors.glassBorderSubtle),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    const Icon(Icons.calendar_month_outlined, size: 14, color: NothingColors.tertiaryCyan),
                                    const SizedBox(width: 6),
                                    Text(
                                      count == 1 ? 'SCHEDULED DATE (1)' : 'SCHEDULED DATES ($count)',
                                      style: NothingTypography.dotMatrix(
                                        fontSize: 10,
                                        color: NothingColors.tertiaryCyan,
                                        letterSpacing: 1.0,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 10),
                                Wrap(
                                  spacing: 6,
                                  runSpacing: 6,
                                  children: uniqueDates.map((dateStr) {
                                    final isToday = DateFormat('yyyy-MM-dd').format(DateTime.now()) == dateStr;
                                    String label = dateStr;
                                    try {
                                      final parsed = DateTime.parse(dateStr);
                                      label = DateFormat('MMM dd').format(parsed).toUpperCase();
                                    } catch (_) {}

                                    return Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                                      decoration: BoxDecoration(
                                        color: isToday
                                            ? NothingColors.crimson.withValues(alpha: 0.25)
                                            : NothingColors.surfaceContainerHigh,
                                        borderRadius: BorderRadius.circular(8),
                                        border: Border.all(
                                          color: isToday ? NothingColors.crimson : NothingColors.glassBorderSubtle,
                                        ),
                                      ),
                                      child: Text(
                                        label,
                                        style: NothingTypography.dotMatrix(
                                          fontSize: 10,
                                          color: isToday ? NothingColors.pureWhite : NothingColors.muted,
                                          fontWeight: isToday ? FontWeight.bold : FontWeight.normal,
                                        ),
                                      ),
                                    );
                                  }).toList(),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    ],

                    // Description / Workout Plan Section
                    if (currentTask.description != null && currentTask.description!.trim().isNotEmpty) ...[
                      const SizedBox(height: 14),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: const Color(0xFF161616),
                          borderRadius: BorderRadius.circular(18),
                          border: Border.all(color: NothingColors.glassBorderSubtle),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Container(
                                  width: 3,
                                  height: 12,
                                  decoration: BoxDecoration(
                                    color: NothingColors.crimson,
                                    borderRadius: BorderRadius.circular(2),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Text(
                                  'WORKOUT PLAN // NOTES',
                                  style: NothingTypography.dotMatrix(
                                    fontSize: 11,
                                    color: NothingColors.muted,
                                    letterSpacing: 1.2,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 10),
                            SelectableText(
                              currentTask.description!,
                              style: NothingTypography.sans(
                                fontSize: 13,
                                height: 1.5,
                                color: NothingColors.pureWhite,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],

                    // Focus Pomodoro Action
                    if (currentTask.hasPomodoro && !isCompleted) ...[
                      const SizedBox(height: 14),
                      GestureDetector(
                        onTap: () {
                          HapticFeedback.mediumImpact();
                          Navigator.of(context).pop();
                          ref.read(pomodoroProvider.notifier).start(currentTask);
                        },
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
                          decoration: BoxDecoration(
                            color: NothingColors.crimson.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: NothingColors.crimson.withValues(alpha: 0.5)),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.timer_outlined, size: 18, color: NothingColors.crimson),
                              const SizedBox(width: 8),
                              Text(
                                'START POMODORO FOCUS',
                                style: NothingTypography.dotMatrix(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: NothingColors.pureWhite,
                                  letterSpacing: 1.2,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],

                    const SizedBox(height: 20),

                    // Actions Row: Complete Toggle, Edit, Delete
                    Row(
                      children: [
                        // Toggle Completion Button
                        Expanded(
                          flex: 3,
                          child: ElevatedButton.icon(
                            onPressed: () {
                              HapticFeedback.lightImpact();
                              ref.read(taskListProvider.notifier).toggleTask(currentTask.id, targetDate: targetDate);
                              Navigator.of(context).pop();
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: isCompleted
                                  ? NothingColors.surfaceContainerHigh
                                  : NothingColors.crimson,
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                            ),
                            icon: Icon(
                              isCompleted ? Icons.undo_rounded : Icons.check_circle_outline_rounded,
                              size: 18,
                              color: NothingColors.pureWhite,
                            ),
                            label: Text(
                              isCompleted ? 'MARK PENDING' : 'MARK COMPLETE',
                              style: NothingTypography.dotMatrix(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: NothingColors.pureWhite,
                                letterSpacing: 1.0,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),

                        // Edit Button
                        Container(
                          decoration: BoxDecoration(
                            color: NothingColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: NothingColors.glassBorderSubtle),
                          ),
                          child: IconButton(
                            icon: const Icon(Icons.edit_outlined, size: 18, color: NothingColors.pureWhite),
                            onPressed: () {
                              HapticFeedback.lightImpact();
                              Navigator.of(context).pop();
                              NewTaskModal.show(context, taskToEdit: currentTask);
                            },
                          ),
                        ),
                        const SizedBox(width: 8),

                        // Delete Button
                        Container(
                          decoration: BoxDecoration(
                            color: NothingColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: NothingColors.glassBorderSubtle),
                          ),
                          child: IconButton(
                            icon: const Icon(Icons.delete_outline_rounded, size: 18, color: NothingColors.crimson),
                            onPressed: () {
                              HapticFeedback.mediumImpact();
                              ref.read(taskListProvider.notifier).deleteTask(currentTask.id);
                              Navigator.of(context).pop();
                            },
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
