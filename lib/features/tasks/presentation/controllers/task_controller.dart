import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/services/glyph_service.dart';
import '../../data/task_repository.dart';
import '../../models/task_model.dart';

final taskRepositoryProvider = Provider<TaskRepository>((ref) {
  return TaskRepository();
});

class TaskController extends StateNotifier<List<TaskModel>> {
  final TaskRepository _repository;

  TaskController(this._repository) : super([]) {
    loadTasks();
  }

  Future<void> loadTasks() async {
    await _repository.init();
    state = _repository.getTasks();
  }

  Future<void> toggleTask(String id, {DateTime? targetDate}) async {
    final effectiveDate = targetDate ?? DateTime.now();
    final dateKey = '${effectiveDate.year}-${effectiveDate.month.toString().padLeft(2, '0')}-${effectiveDate.day.toString().padLeft(2, '0')}';

    state = [
      for (final task in state)
        if (task.id == id)
          if (task.isRoutine)
            task.copyWith(
              completedDates: task.completedDates.contains(dateKey)
                  ? task.completedDates.where((d) => d != dateKey).toList()
                  : [...task.completedDates, dateKey],
            )
          else
            task.copyWith(
              isCompleted: !task.isCompleted,
              status: !task.isCompleted ? 'COMPLETED' : 'UPCOMING',
            )
        else
          task,
    ];
    final updatedTask = state.firstWhere((t) => t.id == id);
    await _repository.saveTask(updatedTask);
  }

  Future<void> addTask(TaskModel task) async {
    state = [...state, task];
    await _repository.saveTask(task);
    _scheduleTaskReminder(task);
  }

  Future<void> updateTask(TaskModel updatedTask) async {
    state = [
      for (final task in state)
        if (task.id == updatedTask.id) updatedTask else task,
    ];
    await _repository.saveTask(updatedTask);
    _scheduleTaskReminder(updatedTask);
  }

  void _scheduleTaskReminder(TaskModel task) {
    if (task.reminderOffsetMinutes != null) {
      final notificationId = task.id.hashCode.abs() % 100000;
      final offsetText = task.reminderOffsetMinutes == 0 
          ? 'Starting now' 
          : '${task.reminderOffsetMinutes}m before start';
      GlyphService.showNotification(
        id: notificationId,
        title: task.title,
        message: '$offsetText • ${task.formattedStartTime} [${task.category}]',
        taskId: task.id,
      );
    }
  }

  Future<void> deleteTask(String id) async {
    state = state.where((t) => t.id != id).toList();
    await _repository.deleteTask(id);
  }

  Future<void> migrateTasksCategory(String oldCategoryName, String newCategoryName) async {
    final updatedList = <TaskModel>[];
    for (final task in state) {
      if (task.category.toUpperCase() == oldCategoryName.toUpperCase()) {
        final updated = task.copyWith(category: newCategoryName.toUpperCase());
        updatedList.add(updated);
        await _repository.saveTask(updated);
      } else {
        updatedList.add(task);
      }
    }
    state = updatedList;
  }

  Future<void> deleteTasksByCategory(String categoryName) async {
    final toDelete = state.where((t) => t.category.toUpperCase() == categoryName.toUpperCase()).toList();
    state = state.where((t) => t.category.toUpperCase() != categoryName.toUpperCase()).toList();
    for (final task in toDelete) {
      await _repository.deleteTask(task.id);
    }
  }

  String exportVault() {
    return _repository.exportVaultJson();
  }

  Future<({int tasksImported, int categoriesImported})> importVault(String jsonString, {required bool mergeMode}) async {
    final result = await _repository.importVaultJson(jsonString, mergeMode: mergeMode);
    await loadTasks();
    return result;
  }
}

final taskListProvider = StateNotifierProvider<TaskController, List<TaskModel>>((ref) {
  final repo = ref.watch(taskRepositoryProvider);
  return TaskController(repo);
});

// Category Controller
class CategoryController extends StateNotifier<List<CategoryModel>> {
  final TaskRepository _repository;

  CategoryController(this._repository) : super([]) {
    loadCategories();
  }

  Future<void> loadCategories() async {
    await _repository.init();
    state = _repository.getCategories();
  }

  Future<void> reloadCategories() async {
    state = _repository.getCategories();
  }

  Future<void> addCategory(String name, int colorValue) async {
    final newCat = CategoryModel(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: name.toUpperCase().trim(),
      colorValue: colorValue,
      isCustom: true,
    );
    state = [...state, newCat];
    await _repository.saveCategory(newCat);
  }

  Future<void> deleteCategory(String id) async {
    if (id.toLowerCase() == 'core') return;
    state = state.where((c) => c.id != id).toList();
    await _repository.deleteCategory(id);
  }
}

final categoryListProvider = StateNotifierProvider<CategoryController, List<CategoryModel>>((ref) {
  final repo = ref.watch(taskRepositoryProvider);
  return CategoryController(repo);
});

// Selected Date Provider for filtering across Home and Calendar
final selectedDateProvider = StateProvider<DateTime>((ref) {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day);
});

// Tasks filtered for the currently selected date (including matching routine tasks)
final tasksForSelectedDateProvider = Provider<List<TaskModel>>((ref) {
  final selected = ref.watch(selectedDateProvider);
  final allTasks = ref.watch(taskListProvider);

  final filtered = allTasks.where((t) {
    return t.matchesDate(selected);
  }).toList();

  filtered.sort((a, b) => a.startFraction.compareTo(b.startFraction));
  return filtered;
});

class TaskStats {
  final int completed;
  final int total;
  final double percentage;

  TaskStats({required this.completed, required this.total, required this.percentage});
}

// Stats specifically for the selected date
final taskStatsForSelectedDateProvider = Provider<TaskStats>((ref) {
  final selected = ref.watch(selectedDateProvider);
  final tasks = ref.watch(tasksForSelectedDateProvider);
  if (tasks.isEmpty) {
    return TaskStats(completed: 0, total: 0, percentage: 0.0);
  }
  final completed = tasks.where((t) => t.isCompletedOn(selected)).length;
  final percentage = (completed / tasks.length);
  return TaskStats(
    completed: completed,
    total: tasks.length,
    percentage: percentage,
  );
});

// Overall stats
final taskStatsProvider = Provider<TaskStats>((ref) {
  final selected = ref.watch(selectedDateProvider);
  final tasks = ref.watch(taskListProvider);
  if (tasks.isEmpty) {
    return TaskStats(completed: 0, total: 0, percentage: 0.0);
  }
  final completed = tasks.where((t) => t.isCompletedOn(selected)).length;
  final percentage = (completed / tasks.length);
  return TaskStats(
    completed: completed,
    total: tasks.length,
    percentage: percentage,
  );
});

// --- POMODORO CONTROLLER & STATE ---
class PomodoroState {
  final TaskModel? activeTask;
  final int totalSeconds;
  final int remainingSeconds;
  final bool isRunning;
  final bool isPaused;

  const PomodoroState({
    this.activeTask,
    this.totalSeconds = 0,
    this.remainingSeconds = 0,
    this.isRunning = false,
    this.isPaused = false,
  });

  double get progress => totalSeconds > 0 ? (remainingSeconds / totalSeconds).clamp(0.0, 1.0) : 0.0;

  String get formattedTime {
    final m = (remainingSeconds ~/ 60).toString().padLeft(2, '0');
    final s = (remainingSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  PomodoroState copyWith({
    TaskModel? activeTask,
    int? totalSeconds,
    int? remainingSeconds,
    bool? isRunning,
    bool? isPaused,
    bool clearTask = false,
  }) {
    return PomodoroState(
      activeTask: clearTask ? null : (activeTask ?? this.activeTask),
      totalSeconds: totalSeconds ?? this.totalSeconds,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      isRunning: isRunning ?? this.isRunning,
      isPaused: isPaused ?? this.isPaused,
    );
  }
}

class PomodoroController extends StateNotifier<PomodoroState> {
  Timer? _timer;

  PomodoroController() : super(const PomodoroState());

  void start(TaskModel task) {
    _timer?.cancel();
    final durationSec = (task.durationMinutes * 60).clamp(60, 14400);
    state = PomodoroState(
      activeTask: task,
      totalSeconds: durationSec,
      remainingSeconds: durationSec,
      isRunning: true,
      isPaused: false,
    );

    GlyphService.updateProgress(1.0);
    GlyphService.triggerReminderPattern();

    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (state.remainingSeconds > 1) {
        final nextRemaining = state.remainingSeconds - 1;
        state = state.copyWith(remainingSeconds: nextRemaining);
        GlyphService.updateProgress(state.progress);
      } else {
        complete();
      }
    });
  }

  void pause() {
    _timer?.cancel();
    state = state.copyWith(isPaused: true, isRunning: false);
  }

  void resume() {
    if (state.activeTask == null || state.remainingSeconds <= 0) return;
    state = state.copyWith(isPaused: false, isRunning: true);
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (state.remainingSeconds > 1) {
        final nextRemaining = state.remainingSeconds - 1;
        state = state.copyWith(remainingSeconds: nextRemaining);
        GlyphService.updateProgress(state.progress);
      } else {
        complete();
      }
    });
  }

  void cancel() {
    _timer?.cancel();
    GlyphService.updateProgress(0.0);
    state = const PomodoroState();
  }

  void complete() {
    _timer?.cancel();
    GlyphService.updateProgress(0.0);
    GlyphService.triggerReminderPattern();
    state = const PomodoroState();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}

final pomodoroProvider = StateNotifierProvider<PomodoroController, PomodoroState>((ref) {
  return PomodoroController();
});

