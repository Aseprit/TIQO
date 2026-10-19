class CategoryModel {
  final String id;
  final String name;
  final int colorValue;
  final bool isCustom;

  const CategoryModel({
    required this.id,
    required this.name,
    required this.colorValue,
    this.isCustom = false,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'colorValue': colorValue,
      'isCustom': isCustom,
    };
  }

  factory CategoryModel.fromMap(Map<String, dynamic> map) {
    return CategoryModel(
      id: map['id'] ?? '',
      name: map['name'] ?? '',
      colorValue: map['colorValue'] ?? 0xFFE11D2A,
      isCustom: map['isCustom'] ?? false,
    );
  }
}

enum TaskType {
  single,
  routine,
  event,
}

enum EventRecurrence {
  none,
  yearly,
  monthly,
}

class TaskModel {
  final String id;
  final String title;
  final String timeRange;
  final String category;
  final bool isCompleted;
  final bool isHighPriority;
  final DateTime date;
  final String status;
  final int startHour;
  final int startMinute;
  final int durationMinutes;
  final bool isRoutine;
  final List<int> routineDays;
  final List<String> completedDates;
  final bool hasPomodoro;
  final int? reminderOffsetMinutes;
  final TaskType taskType;
  final List<String> eventDates;
  final EventRecurrence eventRecurrence;
  final String? description;
  final bool hasSchedule;

  TaskModel({
    required this.id,
    required this.title,
    required this.timeRange,
    required this.category,
    this.isCompleted = false,
    this.isHighPriority = false,
    required this.date,
    this.status = 'UPCOMING',
    this.startHour = 9,
    this.startMinute = 0,
    this.durationMinutes = 60,
    bool isRoutine = false,
    this.routineDays = const [],
    this.completedDates = const [],
    this.hasPomodoro = false,
    this.reminderOffsetMinutes,
    TaskType? taskType,
    this.eventDates = const [],
    this.eventRecurrence = EventRecurrence.none,
    this.description,
    this.hasSchedule = true,
  })  : isRoutine = (taskType == TaskType.routine) || isRoutine,
        taskType = taskType ?? (isRoutine ? TaskType.routine : TaskType.single);

  double get startFraction => startHour + (startMinute / 60.0);
  double get durationHours => durationMinutes / 60.0;

  bool isCompletedOn(DateTime dt) {
    if (taskType == TaskType.routine || isRoutine || taskType == TaskType.event) {
      final key = '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';
      return completedDates.contains(key);
    }
    return isCompleted;
  }

  bool matchesDate(DateTime dt) {
    if (taskType == TaskType.routine || isRoutine) {
      return routineDays.contains(dt.weekday);
    }
    if (taskType == TaskType.event) {
      if (eventRecurrence == EventRecurrence.yearly) {
        return dt.month == date.month && dt.day == date.day;
      }
      if (eventRecurrence == EventRecurrence.monthly) {
        return dt.day == date.day;
      }
      final key = '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';
      return eventDates.contains(key) || (date.year == dt.year && date.month == dt.month && date.day == dt.day);
    }
    return date.year == dt.year && date.month == dt.month && date.day == dt.day;
  }

  String get formattedStartTime {
    if (!hasSchedule) return 'ALL-DAY';
    final h = startHour.toString().padLeft(2, '0');
    final m = startMinute.toString().padLeft(2, '0');
    return '$h:$m';
  }

  String get formattedEndTime {
    if (!hasSchedule) return 'ALL-DAY';
    final endTotalMinutes = (startHour * 60) + startMinute + durationMinutes;
    final endH = ((endTotalMinutes ~/ 60) % 24).toString().padLeft(2, '0');
    final endM = (endTotalMinutes % 60).toString().padLeft(2, '0');
    return '$endH:$endM';
  }

  TaskModel copyWith({
    String? id,
    String? title,
    String? timeRange,
    String? category,
    bool? isCompleted,
    bool? isHighPriority,
    DateTime? date,
    String? status,
    int? startHour,
    int? startMinute,
    int? durationMinutes,
    bool? isRoutine,
    List<int>? routineDays,
    List<String>? completedDates,
    bool? hasPomodoro,
    int? reminderOffsetMinutes,
    bool clearReminder = false,
    TaskType? taskType,
    List<String>? eventDates,
    EventRecurrence? eventRecurrence,
    String? description,
    bool clearDescription = false,
    bool? hasSchedule,
  }) {
    return TaskModel(
      id: id ?? this.id,
      title: title ?? this.title,
      timeRange: timeRange ?? this.timeRange,
      category: category ?? this.category,
      isCompleted: isCompleted ?? this.isCompleted,
      isHighPriority: isHighPriority ?? this.isHighPriority,
      date: date ?? this.date,
      status: status ?? this.status,
      startHour: startHour ?? this.startHour,
      startMinute: startMinute ?? this.startMinute,
      durationMinutes: durationMinutes ?? this.durationMinutes,
      isRoutine: isRoutine ?? this.isRoutine,
      routineDays: routineDays ?? this.routineDays,
      completedDates: completedDates ?? this.completedDates,
      hasPomodoro: hasPomodoro ?? this.hasPomodoro,
      reminderOffsetMinutes: clearReminder ? null : (reminderOffsetMinutes ?? this.reminderOffsetMinutes),
      taskType: taskType ?? this.taskType,
      eventDates: eventDates ?? this.eventDates,
      eventRecurrence: eventRecurrence ?? this.eventRecurrence,
      description: clearDescription ? null : (description ?? this.description),
      hasSchedule: hasSchedule ?? this.hasSchedule,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'timeRange': timeRange,
      'category': category,
      'isCompleted': isCompleted,
      'isHighPriority': isHighPriority,
      'date': date.toIso8601String(),
      'status': status,
      'startHour': startHour,
      'startMinute': startMinute,
      'durationMinutes': durationMinutes,
      'isRoutine': isRoutine,
      'routineDays': routineDays,
      'completedDates': completedDates,
      'hasPomodoro': hasPomodoro,
      'reminderOffsetMinutes': reminderOffsetMinutes,
      'taskType': taskType.name,
      'eventDates': eventDates,
      'eventRecurrence': eventRecurrence.name,
      'description': description,
      'hasSchedule': hasSchedule,
    };
  }

  static ({int startHour, int startMinute, int durationMinutes}) parseTimeInfo(String timeStr) {
    try {
      final clean = timeStr.split('•').first.trim();
      // Case 1: "HH:MM - HH:MM"
      final rangeMatch = RegExp(r'(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})').firstMatch(clean);
      if (rangeMatch != null) {
        final sh = int.parse(rangeMatch.group(1)!);
        final sm = int.parse(rangeMatch.group(2)!);
        final eh = int.parse(rangeMatch.group(3)!);
        final em = int.parse(rangeMatch.group(4)!);
        var dur = ((eh * 60 + em) - (sh * 60 + sm));
        if (dur <= 0) dur = 60;
        return (startHour: sh % 24, startMinute: sm % 60, durationMinutes: dur.clamp(15, 720));
      }

      // Case 2: "HH:MM AM/PM"
      final amPmMatch = RegExp(r'(\d{1,2}):(\d{2})\s*(AM|PM)', caseSensitive: false).firstMatch(clean);
      if (amPmMatch != null) {
        var sh = int.parse(amPmMatch.group(1)!);
        final sm = int.parse(amPmMatch.group(2)!);
        final isPm = amPmMatch.group(3)!.toUpperCase() == 'PM';
        if (isPm && sh < 12) sh += 12;
        if (!isPm && sh == 12) sh = 0;
        return (startHour: sh % 24, startMinute: sm % 60, durationMinutes: 60);
      }

      // Case 3: "HH:MM"
      final simpleMatch = RegExp(r'(\d{1,2}):(\d{2})').firstMatch(clean);
      if (simpleMatch != null) {
        final sh = int.parse(simpleMatch.group(1)!);
        final sm = int.parse(simpleMatch.group(2)!);
        return (startHour: sh % 24, startMinute: sm % 60, durationMinutes: 60);
      }
    } catch (_) {}

    return (startHour: 9, startMinute: 0, durationMinutes: 60);
  }

  factory TaskModel.fromMap(Map<String, dynamic> map) {
    final timeStr = (map['timeRange'] ?? '').toString();
    int? sh = map['startHour'];
    int? sm = map['startMinute'];
    int? dur = map['durationMinutes'];

    // If startHour was null, or if it defaulted to 9:00 but timeRange clearly indicates another hour
    if (sh == null || (sh == 9 && (sm ?? 0) == 0 && timeStr.isNotEmpty && !timeStr.startsWith('09:') && !timeStr.startsWith('9:'))) {
      final parsed = parseTimeInfo(timeStr);
      sh = parsed.startHour;
      sm = parsed.startMinute;
      dur = parsed.durationMinutes;
    }

    final taskTypeName = map['taskType'] as String?;
    TaskType type;
    if (taskTypeName != null) {
      type = TaskType.values.firstWhere(
        (e) => e.name == taskTypeName,
        orElse: () => TaskType.single,
      );
    } else if (map['isRoutine'] == true) {
      type = TaskType.routine;
    } else {
      type = TaskType.single;
    }

    final hasSched = map['hasSchedule'] ??
        (!timeStr.toLowerCase().contains('all-day') && !timeStr.toLowerCase().contains('anytime'));

    final eventRecurrenceName = map['eventRecurrence'] as String?;
    final eventRecurrence = eventRecurrenceName != null
        ? EventRecurrence.values.firstWhere(
            (e) => e.name == eventRecurrenceName,
            orElse: () => EventRecurrence.none,
          )
        : EventRecurrence.none;

    return TaskModel(
      id: map['id'] ?? '',
      title: map['title'] ?? '',
      timeRange: timeStr,
      category: map['category'] ?? 'CORE',
      isCompleted: map['isCompleted'] ?? false,
      isHighPriority: map['isHighPriority'] ?? false,
      date: map['date'] != null ? DateTime.parse(map['date']) : DateTime.now(),
      status: map['status'] ?? 'UPCOMING',
      startHour: sh,
      startMinute: sm ?? 0,
      durationMinutes: dur ?? 60,
      isRoutine: map['isRoutine'] ?? (type == TaskType.routine),
      routineDays: (map['routineDays'] as List<dynamic>?)?.map((e) => (e as num).toInt()).toList() ?? const [],
      completedDates: (map['completedDates'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? const [],
      hasPomodoro: map['hasPomodoro'] ?? false,
      reminderOffsetMinutes: map['reminderOffsetMinutes'] != null ? (map['reminderOffsetMinutes'] as num).toInt() : null,
      taskType: type,
      eventDates: (map['eventDates'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? const [],
      eventRecurrence: eventRecurrence,
      description: map['description'] as String?,
      hasSchedule: hasSched,
    );
  }
}
