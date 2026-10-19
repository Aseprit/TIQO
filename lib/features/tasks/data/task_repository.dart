import 'dart:convert';
import 'package:hive_flutter/hive_flutter.dart';
import '../models/task_model.dart';

class TaskRepository {
  static final TaskRepository _instance = TaskRepository._internal();
  factory TaskRepository() => _instance;
  TaskRepository._internal();

  static const String _taskBoxName = 'tasks_box';
  static const String _categoryBoxName = 'categories_box';
  Box? _taskBox;
  Box? _categoryBox;
  bool _isInitialized = false;

  Future<void> init() async {
    if (_isInitialized) return;

    try {
      if (!Hive.isBoxOpen(_taskBoxName)) {
        _taskBox = await Hive.openBox(_taskBoxName);
      } else {
        _taskBox = Hive.box(_taskBoxName);
      }

      if (!Hive.isBoxOpen(_categoryBoxName)) {
        _categoryBox = await Hive.openBox(_categoryBoxName);
      } else {
        _categoryBox = Hive.box(_categoryBoxName);
      }
    } catch (_) {
      _taskBox = null;
      _categoryBox = null;
    }

    final currentCats = getCategories();
    if (currentCats.isEmpty) {
      await _seedDefaultCategories();
    } else {
      final hasCore = currentCats.any((c) => c.id.toLowerCase() == 'core' || c.name.toUpperCase() == 'CORE');
      if (!hasCore) {
        await saveCategory(const CategoryModel(id: 'core', name: 'CORE', colorValue: 0xFFE11D2A));
      }
    }

    if (getTasks().isEmpty) {
      await _seedDefaultTasks();
    }

    _isInitialized = true;
  }

  Future<void> _seedDefaultCategories() async {
    final defaultCategories = [
      const CategoryModel(id: 'core', name: 'CORE', colorValue: 0xFFE11D2A),
      const CategoryModel(id: 'work', name: 'WORK', colorValue: 0xFFFFFFFF),
      const CategoryModel(id: 'personal', name: 'PERSONAL', colorValue: 0xFF00E5FF),
      const CategoryModel(id: 'health', name: 'HEALTH', colorValue: 0xFFFFB300),
      const CategoryModel(id: 'projects', name: 'PROJECTS', colorValue: 0xFFB388FF),
    ];

    for (final cat in defaultCategories) {
      await saveCategory(cat);
    }
  }

  Future<void> _seedDefaultTasks() async {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final tomorrow = today.add(const Duration(days: 1));

    final defaultTasks = [
      TaskModel(
        id: '1',
        title: 'Deep Focus: Project roadmap review',
        timeRange: '09:00 - 10:30',
        category: 'WORK',
        isCompleted: true,
        date: today,
        status: 'COMPLETED',
        startHour: 9,
        startMinute: 0,
        durationMinutes: 90,
      ),
      TaskModel(
        id: '2',
        title: 'Gym & mobility session',
        timeRange: '11:00 - 12:00',
        category: 'HEALTH',
        isCompleted: true,
        date: today,
        status: 'COMPLETED',
        startHour: 11,
        startMinute: 0,
        durationMinutes: 60,
      ),
      TaskModel(
        id: '3',
        title: 'Design review & UI audit',
        timeRange: '13:30 - 15:00',
        category: 'PROJECTS',
        isCompleted: true,
        date: today,
        status: 'COMPLETED',
        startHour: 13,
        startMinute: 30,
        durationMinutes: 90,
      ),
      TaskModel(
        id: '4',
        title: 'Weekly team sync & priorities',
        timeRange: '15:45 - 16:45',
        category: 'WORK',
        isCompleted: false,
        isHighPriority: true,
        date: today,
        status: 'IN PROGRESS',
        startHour: 15,
        startMinute: 45,
        durationMinutes: 60,
      ),
      TaskModel(
        id: '5',
        title: 'Grocery shopping & errands',
        timeRange: '17:00 - 17:30',
        category: 'PERSONAL',
        isCompleted: false,
        date: today,
        status: 'UPCOMING',
        startHour: 17,
        startMinute: 0,
        durationMinutes: 30,
      ),
      TaskModel(
        id: '6',
        title: 'Quarterly budget & planning',
        timeRange: '14:00 - 15:30',
        category: 'PROJECTS',
        isCompleted: false,
        isHighPriority: true,
        date: tomorrow,
        status: 'SCHEDULED',
        startHour: 14,
        startMinute: 0,
        durationMinutes: 90,
      ),
    ];

    for (final task in defaultTasks) {
      await saveTask(task);
    }
  }

  List<TaskModel> getTasks() {
    if (_taskBox == null) return List.unmodifiable(_inMemoryTasks);
    final List<TaskModel> tasks = [];
    for (var key in _taskBox!.keys) {
      final data = _taskBox!.get(key);
      if (data is Map) {
        tasks.add(TaskModel.fromMap(Map<String, dynamic>.from(data)));
      }
    }
    return tasks;
  }

  Future<void> saveTask(TaskModel task) async {
    if (_taskBox != null) {
      await _taskBox!.put(task.id, task.toMap());
    } else {
      _inMemoryTasks.removeWhere((t) => t.id == task.id);
      _inMemoryTasks.add(task);
    }
  }

  Future<void> deleteTask(String id) async {
    if (_taskBox != null) {
      await _taskBox!.delete(id);
    } else {
      _inMemoryTasks.removeWhere((t) => t.id == id);
    }
  }

  List<CategoryModel> getCategories() {
    if (_categoryBox == null) return List.unmodifiable(_inMemoryCategories);
    final List<CategoryModel> categories = [];
    for (var key in _categoryBox!.keys) {
      final data = _categoryBox!.get(key);
      if (data is Map) {
        categories.add(CategoryModel.fromMap(Map<String, dynamic>.from(data)));
      }
    }
    return categories;
  }

  Future<void> saveCategory(CategoryModel category) async {
    if (_categoryBox != null) {
      await _categoryBox!.put(category.id, category.toMap());
    } else {
      _inMemoryCategories.removeWhere((c) => c.id == category.id);
      _inMemoryCategories.add(category);
    }
  }

  Future<void> deleteCategory(String id) async {
    // CORE category cannot be deleted
    if (id.toLowerCase() == 'core') return;

    if (_categoryBox != null) {
      await _categoryBox!.delete(id);
    } else {
      _inMemoryCategories.removeWhere((c) => c.id == id);
    }
  }

  String exportVaultJson() {
    final categories = getCategories().map((c) => c.toMap()).toList();
    final tasks = getTasks().map((t) => t.toMap()).toList();
    final vaultData = {
      'version': '1.0',
      'appName': 'TIQO',
      'exportedAt': DateTime.now().toIso8601String(),
      'categories': categories,
      'tasks': tasks,
    };
    return const JsonEncoder.withIndent('  ').convert(vaultData);
  }

  Future<({int tasksImported, int categoriesImported})> importVaultJson(
    String jsonString, {
    required bool mergeMode,
  }) async {
    final dynamic parsed = jsonDecode(jsonString);
    if (parsed is! Map) {
      throw const FormatException('Invalid vault format: Expected JSON object.');
    }
    final map = Map<String, dynamic>.from(parsed);
    final rawCategories = map['categories'];
    final rawTasks = map['tasks'];

    if (rawTasks == null && rawCategories == null) {
      throw const FormatException('Invalid vault format: Missing tasks or categories.');
    }

    if (!mergeMode) {
      // Clear all existing tasks and categories
      if (_taskBox != null) {
        await _taskBox!.clear();
      } else {
        _inMemoryTasks.clear();
      }
      if (_categoryBox != null) {
        await _categoryBox!.clear();
      } else {
        _inMemoryCategories.clear();
      }
    }

    // Always ensure CORE exists
    final currentCats = getCategories();
    final bool coreExists = currentCats.any((c) => c.id.toLowerCase() == 'core' || c.name.toUpperCase() == 'CORE');
    if (!coreExists) {
      await saveCategory(const CategoryModel(id: 'core', name: 'CORE', colorValue: 0xFFE11D2A));
    }

    int categoriesImported = 0;
    if (rawCategories is List) {
      for (final item in rawCategories) {
        if (item is Map) {
          final cat = CategoryModel.fromMap(Map<String, dynamic>.from(item));
          final existing = getCategories();
          final exists = existing.any((c) => c.id == cat.id || c.name.toUpperCase() == cat.name.toUpperCase());
          if (!exists || !mergeMode) {
            await saveCategory(cat);
            categoriesImported++;
          }
        }
      }
    }

    int tasksImported = 0;
    if (rawTasks is List) {
      final existingTasks = getTasks();
      final existingIds = existingTasks.map((t) => t.id).toSet();

      for (final item in rawTasks) {
        if (item is Map) {
          var taskMap = Map<String, dynamic>.from(item);
          // If in merge mode and ID collides, generate a unique ID
          if (mergeMode && existingIds.contains(taskMap['id']?.toString())) {
            taskMap['id'] = '${taskMap['id']}_${DateTime.now().microsecondsSinceEpoch}';
          }
          final task = TaskModel.fromMap(taskMap);
          // If task category doesn't exist, create it automatically
          final catsNow = getCategories();
          final hasCat = catsNow.any((c) => c.name.toUpperCase() == task.category.toUpperCase());
          if (!hasCat && task.category.isNotEmpty) {
            await saveCategory(CategoryModel(
              id: task.category.toLowerCase().replaceAll(RegExp(r'\s+'), '_'),
              name: task.category.toUpperCase(),
              colorValue: 0xFFFFFFFF,
              isCustom: true,
            ));
            categoriesImported++;
          }
          await saveTask(task);
          existingIds.add(task.id);
          tasksImported++;
        }
      }
    }

    return (tasksImported: tasksImported, categoriesImported: categoriesImported);
  }

  static final List<TaskModel> _inMemoryTasks = [];
  static final List<CategoryModel> _inMemoryCategories = [];
}
