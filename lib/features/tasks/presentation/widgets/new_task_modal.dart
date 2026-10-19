import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/nothing_theme.dart';
import '../../../../core/widgets/nothing_toggle.dart';
import '../../models/task_model.dart';
import '../controllers/task_controller.dart';

class NewTaskModal extends ConsumerStatefulWidget {
  final TaskModel? taskToEdit;

  const NewTaskModal({super.key, this.taskToEdit});

  static Future<void> show(BuildContext context, {TaskModel? taskToEdit}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => NewTaskModal(taskToEdit: taskToEdit),
    );
  }

  @override
  ConsumerState<NewTaskModal> createState() => _NewTaskModalState();
}

class _NewTaskModalState extends ConsumerState<NewTaskModal> {
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _descriptionController = TextEditingController();
  final TextEditingController _newCategoryController = TextEditingController();

  TaskType _taskType = TaskType.single;
  EventRecurrence _eventRecurrence = EventRecurrence.none;
  String _selectedCategory = 'CORE';
  late DateTime _selectedDate;
  bool _hasSchedule = false;
  int _startHour = 12;
  int _startMinute = 0;
  int _durationMinutes = 60;

  bool _isDescriptionExpanded = false;
  bool _isMoreOptionsExpanded = false;
  List<int> _selectedRoutineDays = [];
  List<String> _eventDates = [];
  DateTime _eventPickerMonth = DateTime.now();

  bool _hasPomodoro = false;
  int? _reminderOffsetMinutes;

  bool _isCreatingCategory = false;
  bool _isDismissing = false;
  int _selectedColorValue = 0xFFE11D2A;

  void _safeDismiss() {
    if (!_isDismissing && mounted && Navigator.of(context).canPop()) {
      _isDismissing = true;
      HapticFeedback.lightImpact();
      Navigator.of(context).pop();
    }
  }

  final List<int> _availableColors = [
    0xFFE11D2A, // Crimson
    0xFF00E5FF, // Cyan
    0xFFFFB300, // Amber
    0xFFFFFFFF, // Pure White
    0xFFB388FF, // Purple
    0xFF76FF03, // Lime
  ];

  @override
  void initState() {
    super.initState();
    if (widget.taskToEdit != null) {
      final t = widget.taskToEdit!;
      _titleController.text = t.title;
      _descriptionController.text = t.description ?? '';
      _isDescriptionExpanded = (t.description != null && t.description!.trim().isNotEmpty);
      _taskType = t.taskType;
      _eventRecurrence = t.eventRecurrence;
      _eventDates = t.eventDates.toSet().toList()..sort();
      _hasSchedule = t.hasSchedule;
      _selectedCategory = t.category;
      _selectedDate = t.date;
      _startHour = t.startHour;
      _startMinute = t.startMinute;
      _durationMinutes = t.durationMinutes;
      _selectedRoutineDays = List.from(t.routineDays);
      _hasPomodoro = t.hasPomodoro;
      _reminderOffsetMinutes = t.reminderOffsetMinutes;
      _eventPickerMonth = t.date;
      _isMoreOptionsExpanded = (t.hasPomodoro || t.reminderOffsetMinutes != null || t.category != 'CORE');
    } else {
      _selectedCategory = 'CORE';
      _eventRecurrence = EventRecurrence.none;
      _selectedDate = ref.read(selectedDateProvider);
      _startHour = 12;
      _startMinute = 0;
      final key = DateFormat('yyyy-MM-dd').format(_selectedDate);
      _eventDates = [key];
      _eventPickerMonth = _selectedDate;
      _selectedRoutineDays = [_selectedDate.weekday];
      _hasSchedule = false;
      _isMoreOptionsExpanded = false;
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _newCategoryController.dispose();
    super.dispose();
  }

  void _submit() {
    final title = _titleController.text.trim();
    if (title.isEmpty) return;

    final description = _descriptionController.text.trim().isEmpty ? null : _descriptionController.text.trim();

    String timeRange;
    if (!_hasSchedule) {
      timeRange = 'ALL-DAY';
    } else {
      final startHStr = _startHour.toString().padLeft(2, '0');
      final startMStr = _startMinute.toString().padLeft(2, '0');
      final endTotalMinutes = (_startHour * 60) + _startMinute + _durationMinutes;
      final endHStr = ((endTotalMinutes ~/ 60) % 24).toString().padLeft(2, '0');
      final endMStr = (endTotalMinutes % 60).toString().padLeft(2, '0');
      timeRange = '$startHStr:$startMStr - $endHStr:$endMStr';
    }

    List<String> finalEventDates = _eventDates.toSet().toList()..sort();
    if (_taskType == TaskType.event && finalEventDates.isEmpty) {
      finalEventDates = [DateFormat('yyyy-MM-dd').format(_selectedDate)];
    }

    if (widget.taskToEdit != null) {
      final updatedTask = widget.taskToEdit!.copyWith(
        title: title,
        description: description,
        clearDescription: description == null,
        taskType: _taskType,
        eventDates: finalEventDates,
        eventRecurrence: _taskType == TaskType.event ? _eventRecurrence : EventRecurrence.none,
        hasSchedule: _hasSchedule,
        timeRange: timeRange,
        category: _selectedCategory,
        date: _selectedDate,
        startHour: _startHour,
        startMinute: _startMinute,
        durationMinutes: _durationMinutes,
        isRoutine: _taskType == TaskType.routine,
        routineDays: _selectedRoutineDays,
        hasPomodoro: _hasPomodoro,
        reminderOffsetMinutes: _reminderOffsetMinutes,
        clearReminder: _reminderOffsetMinutes == null,
      );
      ref.read(taskListProvider.notifier).updateTask(updatedTask);
      HapticFeedback.lightImpact();
    } else {
      final newTask = TaskModel(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        title: title,
        description: description,
        taskType: _taskType,
        eventDates: finalEventDates,
        eventRecurrence: _taskType == TaskType.event ? _eventRecurrence : EventRecurrence.none,
        hasSchedule: _hasSchedule,
        timeRange: timeRange,
        category: _selectedCategory,
        isCompleted: false,
        date: _selectedDate,
        status: 'UPCOMING',
        startHour: _startHour,
        startMinute: _startMinute,
        durationMinutes: _durationMinutes,
        isRoutine: _taskType == TaskType.routine,
        routineDays: _selectedRoutineDays,
        hasPomodoro: _hasPomodoro,
        reminderOffsetMinutes: _reminderOffsetMinutes,
      );
      ref.read(taskListProvider.notifier).addTask(newTask);
      HapticFeedback.mediumImpact();
    }

    _safeDismiss();
  }

  void _saveNewCategory() {
    final catName = _newCategoryController.text.trim().toUpperCase();
    if (catName.isEmpty) return;

    ref.read(categoryListProvider.notifier).addCategory(catName, _selectedColorValue);
    setState(() {
      _selectedCategory = catName;
      _isCreatingCategory = false;
      _newCategoryController.clear();
    });
    HapticFeedback.selectionClick();
  }

  @override
  Widget build(BuildContext context) {
    final categories = ref.watch(categoryListProvider);
    final mediaQuery = MediaQuery.of(context);
    final maxHeight = (mediaQuery.size.height - mediaQuery.viewInsets.bottom) * 0.90;

    return Padding(
      padding: EdgeInsets.only(
        bottom: mediaQuery.viewInsets.bottom,
      ),
      child: ClipRRect(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        child: Container(
          constraints: BoxConstraints(
            maxHeight: maxHeight,
          ),
          decoration: BoxDecoration(
            color: const Color(0xFF121212),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
            border: Border.all(color: NothingColors.glassBorderSubtle, width: 1),
          ),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // 1. Fixed Top Header & Drag Handle (outside scrollview, allows swipe-down to dismiss)
                _buildTopHeaderAndHandle(),

                // 2. Scrollable Body
                Flexible(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Task Type 3-Segment Switcher
                        Container(
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            color: NothingColors.surfaceContainerLowest,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: NothingColors.glassBorderSubtle),
                          ),
                          child: Row(
                            children: [
                              _buildTypeSegment('SINGLE TASK', TaskType.single),
                              _buildTypeSegment('ROUTINE', TaskType.routine),
                              _buildTypeSegment('EVENT', TaskType.event),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),

                        // Task Title & Expandable Description Card
                        _buildTitleAndDescriptionCard(),
                        const SizedBox(height: 16),

                        // Type-Specific Date / Routine / Event Section
                        if (_taskType == TaskType.single) ...[
                          _buildFullWidthDatePicker(),
                        ] else if (_taskType == TaskType.routine) ...[
                          _buildRoutineDayPicker(),
                        ] else if (_taskType == TaskType.event) ...[
                          _buildMultiDayEventPicker(),
                        ],
                        const SizedBox(height: 14),

                        // Schedule Time Toggle Card
                        _buildScheduleTimeCard(),
                        const SizedBox(height: 14),

                        // Expandable More Options (Category, Reminder, Pomodoro)
                        _buildMoreOptionsSection(categories),
                        const SizedBox(height: 16),
                      ],
                    ),
                  ),
                ),

                // 3. Pinned Bottom Action Button (Sticky Footer above System Navigation Bar)
                _buildPinnedBottomAction(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTypeSegment(String title, TaskType type) {
    final isSelected = _taskType == type;
    return Expanded(
      child: GestureDetector(
        onTap: () {
          HapticFeedback.selectionClick();
          setState(() => _taskType = type);
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: isSelected ? NothingColors.crimson : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Center(
            child: Text(
              title,
              style: NothingTypography.dotMatrix(
                fontSize: 10,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                color: isSelected ? NothingColors.pureWhite : NothingColors.muted,
                letterSpacing: 1.0,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTopHeaderAndHandle() {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onVerticalDragUpdate: (details) {
        if (details.primaryDelta != null && details.primaryDelta! > 1.5) {
          _safeDismiss();
        }
      },
      onVerticalDragEnd: (details) {
        if (details.primaryVelocity != null && details.primaryVelocity! > 30) {
          _safeDismiss();
        }
      },
      child: Padding(
        padding: const EdgeInsets.only(top: 12, bottom: 8, left: 20, right: 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Drag handle pill
            Center(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: _safeDismiss,
                child: Container(
                  width: 44,
                  height: 5,
                  decoration: BoxDecoration(
                    color: NothingColors.surfaceBright,
                    borderRadius: BorderRadius.circular(2.5),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 14),
            // Header row with Dismiss action
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(left: 4, right: 10),
                      child: Container(
                        width: 9,
                        height: 9,
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
                    ),
                    Text(
                      widget.taskToEdit != null ? 'EDIT TASK' : 'NEW TASK',
                      style: NothingTypography.dotMatrix(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.2,
                      ),
                    ),
                  ],
                ),
                GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: _safeDismiss,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                    child: Text(
                      '[DISMISS]',
                      style: NothingTypography.dotMatrix(
                        fontSize: 10,
                        color: NothingColors.muted,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 0.8,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTitleAndDescriptionCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: NothingColors.glassSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: NothingColors.glassBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'TASK TITLE',
            style: NothingTypography.dotMatrix(
              fontSize: 11,
              color: NothingColors.muted,
              letterSpacing: 1.2,
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _titleController,
            autofocus: widget.taskToEdit == null,
            style: NothingTypography.dotMatrix(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: NothingColors.pureWhite,
            ),
            decoration: InputDecoration(
              hintText: 'What needs to be done?',
              hintStyle: NothingTypography.dotMatrix(
                fontSize: 14,
                color: NothingColors.muted.withValues(alpha: 0.6),
              ),
              border: InputBorder.none,
              isDense: true,
              contentPadding: EdgeInsets.zero,
            ),
          ),
          const SizedBox(height: 12),
          const Divider(color: NothingColors.glassBorderSubtle, height: 1),
          const SizedBox(height: 12),

          // Description toggle & field
          if (!_isDescriptionExpanded) ...[
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () {
                HapticFeedback.selectionClick();
                setState(() => _isDescriptionExpanded = true);
              },
              child: Row(
                children: [
                  const Icon(Icons.add_rounded, size: 14, color: NothingColors.crimson),
                  const SizedBox(width: 6),
                  Text(
                    'ADD DESCRIPTION',
                    style: NothingTypography.dotMatrix(
                      fontSize: 10,
                      color: NothingColors.crimson,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 0.8,
                    ),
                  ),
                ],
              ),
            ),
          ] else ...[
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'DESCRIPTION // NOTES (OPTIONAL)',
                  style: NothingTypography.dotMatrix(
                    fontSize: 10,
                    color: NothingColors.muted,
                    letterSpacing: 1.0,
                  ),
                ),
                GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () {
                    HapticFeedback.selectionClick();
                    setState(() {
                      _descriptionController.clear();
                      _isDescriptionExpanded = false;
                    });
                  },
                  child: Text(
                    '[CLEAR]',
                    style: NothingTypography.dotMatrix(
                      fontSize: 10,
                      color: NothingColors.muted,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _descriptionController,
              maxLines: 4,
              style: NothingTypography.sans(
                fontSize: 13,
                height: 1.4,
                color: NothingColors.pureWhite,
              ),
              decoration: InputDecoration(
                hintText: 'Add description, workout plan, or notes...',
                hintStyle: NothingTypography.sans(
                  fontSize: 12,
                  color: NothingColors.muted.withValues(alpha: 0.5),
                ),
                border: InputBorder.none,
                isDense: true,
                contentPadding: EdgeInsets.zero,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildFullWidthDatePicker() {
    final isToday = DateUtils.isSameDay(_selectedDate, DateTime.now());
    final isTomorrow = DateUtils.isSameDay(_selectedDate, DateTime.now().add(const Duration(days: 1)));

    String datePrefix = '';
    if (isToday) {
      datePrefix = 'TODAY // ';
    } else if (isTomorrow) {
      datePrefix = 'TOMORROW // ';
    }

    final formattedDate = '$datePrefix${DateFormat('EEEE, MMM dd, yyyy').format(_selectedDate).toUpperCase()}';

    return Container(
      decoration: BoxDecoration(
        color: NothingColors.glassSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: NothingColors.glassBorderSubtle),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: () async {
            HapticFeedback.selectionClick();
            final picked = await showDatePicker(
              context: context,
              initialDate: _selectedDate,
              firstDate: DateTime.now().subtract(const Duration(days: 365)),
              lastDate: DateTime.now().add(const Duration(days: 730)),
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
              setState(() => _selectedDate = picked);
            }
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            child: Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    color: NothingColors.surfaceContainerHigh,
                  ),
                  child: const Icon(Icons.calendar_today_rounded, size: 15, color: NothingColors.crimson),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'TASK DATE',
                        style: NothingTypography.dotMatrix(
                          fontSize: 9,
                          color: NothingColors.muted,
                          letterSpacing: 1.0,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        formattedDate,
                        style: NothingTypography.dotMatrix(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: NothingColors.pureWhite,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                const Icon(
                  Icons.edit_calendar_rounded,
                  size: 18,
                  color: NothingColors.muted,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildScheduleTimeCard() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: NothingColors.glassSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: _hasSchedule
              ? NothingColors.crimson.withValues(alpha: 0.5)
              : NothingColors.glassBorderSubtle,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 28,
                    height: 28,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: _hasSchedule ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
                    ),
                    child: const Icon(Icons.access_time_rounded, size: 16, color: NothingColors.pureWhite),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'SCHEDULE TIME',
                        style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        _hasSchedule ? 'Hourly timeline placement' : 'All-day / anytime task',
                        style: NothingTypography.sans(fontSize: 10, color: NothingColors.muted),
                      ),
                    ],
                  ),
                ],
              ),
              NothingToggle(
                value: _hasSchedule,
                onChanged: (val) {
                  HapticFeedback.selectionClick();
                  setState(() => _hasSchedule = val);
                },
              ),
            ],
          ),

          if (_hasSchedule) ...[
            const SizedBox(height: 14),
            Row(
              children: [
                // Start Time Dropdowns (HH : MM)
                Expanded(
                  flex: 3,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                      color: NothingColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: NothingColors.glassBorderSubtle),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('TIME:', style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted, letterSpacing: 0.8)),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            DropdownButton<int>(
                              value: _startHour,
                              dropdownColor: NothingColors.surfaceContainerLowest,
                              underline: const SizedBox.shrink(),
                              isDense: true,
                              icon: const SizedBox.shrink(),
                              alignment: Alignment.center,
                              items: List.generate(24, (i) => i).map((h) {
                                return DropdownMenuItem(
                                  value: h,
                                  alignment: Alignment.center,
                                  child: Text(
                                    h.toString().padLeft(2, '0'),
                                    style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold, color: NothingColors.pureWhite),
                                  ),
                                );
                              }).toList(),
                              onChanged: (val) {
                                if (val != null) setState(() => _startHour = val);
                              },
                            ),
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 4),
                              child: Text(
                                ':',
                                style: NothingTypography.dotMatrix(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: NothingColors.crimson,
                                ),
                              ),
                            ),
                            DropdownButton<int>(
                              value: _startMinute,
                              dropdownColor: NothingColors.surfaceContainerLowest,
                              underline: const SizedBox.shrink(),
                              isDense: true,
                              icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 14, color: NothingColors.muted),
                              alignment: Alignment.center,
                              items: [0, 15, 30, 45].map((m) {
                                return DropdownMenuItem(
                                  value: m,
                                  alignment: Alignment.center,
                                  child: Text(
                                    m.toString().padLeft(2, '0'),
                                    style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold, color: NothingColors.pureWhite),
                                  ),
                                );
                              }).toList(),
                              onChanged: (val) {
                                if (val != null) setState(() => _startMinute = val);
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                // Duration Selector
                Expanded(
                  flex: 2,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                      color: NothingColors.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: NothingColors.glassBorderSubtle),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('FOR:', style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted, letterSpacing: 0.8)),
                        DropdownButton<int>(
                          value: _durationMinutes,
                          dropdownColor: NothingColors.surfaceContainerLowest,
                          underline: const SizedBox.shrink(),
                          isDense: true,
                          icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 14, color: NothingColors.muted),
                          items: [
                            (15, '15m'),
                            (30, '30m'),
                            (45, '45m'),
                            (60, '1h'),
                            (90, '1.5h'),
                            (120, '2h'),
                            (180, '3h'),
                          ].map((pair) {
                            return DropdownMenuItem<int>(
                              value: pair.$1,
                              child: Text(
                                pair.$2,
                                style: NothingTypography.dotMatrix(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: NothingColors.pureWhite,
                                ),
                              ),
                            );
                          }).toList(),
                          onChanged: (val) {
                            if (val != null) setState(() => _durationMinutes = val);
                          },
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildMoreOptionsSection(List<CategoryModel> categories) {
    if (!_isMoreOptionsExpanded) {
      final currentCat = categories.firstWhere(
        (c) => c.name == _selectedCategory,
        orElse: () => categories.isNotEmpty ? categories.first : const CategoryModel(id: 'core', name: 'CORE', colorValue: 0xFFE11D2A),
      );

      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          HapticFeedback.selectionClick();
          setState(() => _isMoreOptionsExpanded = true);
        },
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          decoration: BoxDecoration(
            color: NothingColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: NothingColors.glassBorderSubtle),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.tune_rounded, size: 14, color: NothingColors.muted),
                  const SizedBox(width: 8),
                  Text(
                    '+ MORE OPTIONS',
                    style: NothingTypography.dotMatrix(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: NothingColors.muted,
                      letterSpacing: 0.8,
                    ),
                  ),
                ],
              ),
              Row(
                children: [
                  Container(
                    width: 6,
                    height: 6,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Color(currentCat.colorValue),
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    _selectedCategory,
                    style: NothingTypography.dotMatrix(
                      fontSize: 9,
                      fontWeight: FontWeight.bold,
                      color: NothingColors.pureWhite,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Icon(Icons.keyboard_arrow_down_rounded, size: 14, color: NothingColors.muted),
                ],
              ),
            ],
          ),
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: NothingColors.glassSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: NothingColors.glassBorderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'ADDITIONAL SETTINGS',
                style: NothingTypography.dotMatrix(
                  fontSize: 10,
                  color: NothingColors.muted,
                  letterSpacing: 1.2,
                ),
              ),
              GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () {
                  HapticFeedback.selectionClick();
                  setState(() => _isMoreOptionsExpanded = false);
                },
                child: Text(
                  '[HIDE OPTIONS]',
                  style: NothingTypography.dotMatrix(
                    fontSize: 10,
                    color: NothingColors.crimson,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 1. Task Category Selector & Creator
          _buildCategorySectionInMoreOptions(categories),
          const SizedBox(height: 12),
          const Divider(color: NothingColors.glassBorderSubtle, height: 1),
          const SizedBox(height: 12),

          // 2. Reminder Selector
          _buildReminderRow(),
          const SizedBox(height: 12),
          const Divider(color: NothingColors.glassBorderSubtle, height: 1),
          const SizedBox(height: 12),

          // 3. Pomodoro Focus Timer Toggle
          _buildPomodoroRow(),
        ],
      ),
    );
  }

  Widget _buildCategorySectionInMoreOptions(List<CategoryModel> categories) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(
                  width: 26,
                  height: 26,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    color: NothingColors.surfaceContainerHigh,
                  ),
                  child: const Icon(Icons.label_outline_rounded, size: 14, color: NothingColors.pureWhite),
                ),
                const SizedBox(width: 10),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('TASK CATEGORY', style: NothingTypography.dotMatrix(fontSize: 11, fontWeight: FontWeight.bold)),
                    Text('Organization tag', style: NothingTypography.sans(fontSize: 9, color: NothingColors.muted)),
                  ],
                ),
              ],
            ),
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => setState(() => _isCreatingCategory = !_isCreatingCategory),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                child: Text(
                  _isCreatingCategory ? '[CANCEL]' : '[+ CREATE]',
                  style: NothingTypography.dotMatrix(
                    fontSize: 10,
                    color: NothingColors.crimson,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: categories.map((cat) {
            final isSelected = _selectedCategory == cat.name;
            return GestureDetector(
              onTap: () {
                HapticFeedback.selectionClick();
                setState(() => _selectedCategory = cat.name);
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: isSelected ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(
                    color: isSelected ? NothingColors.crimson : NothingColors.glassBorderSubtle,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 5,
                      height: 5,
                      decoration: BoxDecoration(shape: BoxShape.circle, color: Color(cat.colorValue)),
                    ),
                    const SizedBox(width: 5),
                    Text(
                      cat.name,
                      style: NothingTypography.dotMatrix(
                        fontSize: 10,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                        color: isSelected ? NothingColors.pureWhite : NothingColors.muted,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }).toList(),
        ),
        if (_isCreatingCategory) ...[
          const SizedBox(height: 10),
          _buildInlineCategoryCreator(),
        ],
      ],
    );
  }

  Widget _buildReminderRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Container(
              width: 26,
              height: 26,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: _reminderOffsetMinutes != null ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
              ),
              child: const Icon(Icons.notifications_active_outlined, size: 14, color: NothingColors.pureWhite),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('REMINDER', style: NothingTypography.dotMatrix(fontSize: 11, fontWeight: FontWeight.bold)),
                Text('Glyph flash & alert', style: NothingTypography.sans(fontSize: 9, color: NothingColors.muted)),
              ],
            ),
          ],
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: NothingColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: NothingColors.glassBorderSubtle),
          ),
          child: DropdownButton<int?>(
            value: _reminderOffsetMinutes,
            dropdownColor: NothingColors.surfaceContainerLowest,
            underline: const SizedBox.shrink(),
            isDense: true,
            icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 14, color: NothingColors.muted),
            items: const [
              (null, 'NONE'),
              (0, 'AT START'),
              (5, '5M BEFORE'),
              (15, '15M BEFORE'),
              (30, '30M BEFORE'),
              (1440, '1 DAY BEFORE'),
            ].map((pair) {
              return DropdownMenuItem<int?>(
                value: pair.$1,
                child: Text(
                  pair.$2,
                  style: NothingTypography.dotMatrix(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: NothingColors.pureWhite,
                    letterSpacing: 0.8,
                  ),
                ),
              );
            }).toList(),
            onChanged: (val) {
              HapticFeedback.selectionClick();
              setState(() => _reminderOffsetMinutes = val);
            },
          ),
        ),
      ],
    );
  }

  Widget _buildPomodoroRow() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Container(
              width: 26,
              height: 26,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: _hasPomodoro ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
              ),
              child: const Icon(Icons.timer_outlined, size: 14, color: NothingColors.pureWhite),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('POMODORO FOCUS', style: NothingTypography.dotMatrix(fontSize: 11, fontWeight: FontWeight.bold)),
                Text('Glyph countdown bar', style: NothingTypography.sans(fontSize: 9, color: NothingColors.muted)),
              ],
            ),
          ],
        ),
        NothingToggle(
          value: _hasPomodoro,
          onChanged: (val) {
            HapticFeedback.selectionClick();
            setState(() => _hasPomodoro = val);
          },
        ),
      ],
    );
  }

  Widget _buildPinnedBottomAction() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      decoration: const BoxDecoration(
        color: Color(0xFF141414),
        border: Border(
          top: BorderSide(color: NothingColors.glassBorderSubtle, width: 1),
        ),
      ),
      child: SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: _submit,
          style: ElevatedButton.styleFrom(
            backgroundColor: NothingColors.crimson,
            padding: const EdgeInsets.symmetric(vertical: 16),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            elevation: 0,
          ),
          child: Text(
            widget.taskToEdit != null ? 'SAVE CHANGES' : 'CREATE TASK',
            style: NothingTypography.dotMatrix(
              fontSize: 13,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.5,
              color: NothingColors.pureWhite,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInlineCategoryCreator() {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: NothingColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: NothingColors.crimson.withValues(alpha: 0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: _newCategoryController,
            style: NothingTypography.dotMatrix(fontSize: 12, color: NothingColors.pureWhite),
            decoration: InputDecoration(
              hintText: 'New category name (e.g. GYM)...',
              hintStyle: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted),
              isDense: true,
              border: InputBorder.none,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Text('COLOR: ', style: NothingTypography.dotMatrix(fontSize: 9, color: NothingColors.muted)),
              const SizedBox(width: 6),
              Expanded(
                child: Row(
                  children: _availableColors.map((cVal) {
                    final isSelected = _selectedColorValue == cVal;
                    return GestureDetector(
                      onTap: () => setState(() => _selectedColorValue = cVal),
                      child: Container(
                        width: 18,
                        height: 18,
                        margin: const EdgeInsets.only(right: 6),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Color(cVal),
                          border: isSelected ? Border.all(color: NothingColors.pureWhite, width: 2) : null,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
              ElevatedButton(
                onPressed: _saveNewCategory,
                style: ElevatedButton.styleFrom(
                  backgroundColor: NothingColors.crimson,
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
                ),
                child: Text('ADD', style: NothingTypography.dotMatrix(fontSize: 9, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildRoutineDayPicker() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: NothingColors.glassSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: NothingColors.crimson.withValues(alpha: 0.5)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: NothingColors.crimson,
                ),
                child: const Icon(Icons.repeat_rounded, size: 16, color: NothingColors.pureWhite),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'WEEKLY REPETITION',
                    style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                  Text(
                    'Select days of week when this routine occurs',
                    style: NothingTypography.sans(fontSize: 10, color: NothingColors.muted),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _buildDayChip('MON', 1),
              _buildDayChip('TUE', 2),
              _buildDayChip('WED', 3),
              _buildDayChip('THU', 4),
              _buildDayChip('FRI', 5),
              _buildDayChip('SAT', 6),
              _buildDayChip('SUN', 7),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMultiDayEventPicker() {
    final firstDayOfMonth = DateTime(_eventPickerMonth.year, _eventPickerMonth.month, 1);
    final daysInMonth = DateUtils.getDaysInMonth(_eventPickerMonth.year, _eventPickerMonth.month);
    final startingWeekday = firstDayOfMonth.weekday; // 1: Mon, 7: Sun

    String subtitleText;
    if (_eventRecurrence == EventRecurrence.yearly) {
      subtitleText = 'Repeats annually on ${DateFormat('MMM dd').format(_selectedDate)}';
    } else if (_eventRecurrence == EventRecurrence.monthly) {
      subtitleText = 'Repeats monthly on day ${_selectedDate.day}';
    } else {
      subtitleText = '${_eventDates.length} day(s) selected';
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: NothingColors.glassSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: NothingColors.tertiaryCyan.withValues(alpha: 0.5)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Event Recurrence Segment
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
            decoration: BoxDecoration(
              color: NothingColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: NothingColors.glassBorderSubtle),
            ),
            child: Row(
              children: [
                _buildRecurrencePill('ONE-TIME', EventRecurrence.none),
                _buildRecurrencePill('YEARLY', EventRecurrence.yearly),
                _buildRecurrencePill('MONTHLY', EventRecurrence.monthly),
              ],
            ),
          ),
          const SizedBox(height: 14),

          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Row(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: NothingColors.tertiaryCyan.withValues(alpha: 0.2),
                        border: Border.all(color: NothingColors.tertiaryCyan),
                      ),
                      child: const Icon(Icons.calendar_month_outlined, size: 15, color: NothingColors.tertiaryCyan),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _eventRecurrence == EventRecurrence.yearly
                                ? 'ANNUAL EVENT'
                                : (_eventRecurrence == EventRecurrence.monthly ? 'MONTHLY EVENT' : 'EVENT DATES'),
                            style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold),
                          ),
                          Text(
                            subtitleText,
                            style: NothingTypography.sans(fontSize: 10, color: NothingColors.tertiaryCyan),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              // Month Switcher
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  GestureDetector(
                    onTap: () {
                      HapticFeedback.lightImpact();
                      setState(() {
                        _eventPickerMonth = DateTime(_eventPickerMonth.year, _eventPickerMonth.month - 1, 1);
                      });
                    },
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 2),
                      child: Icon(Icons.chevron_left_rounded, size: 18, color: NothingColors.muted),
                    ),
                  ),
                  Text(
                    DateFormat('MMM yyyy').format(_eventPickerMonth).toUpperCase(),
                    style: NothingTypography.dotMatrix(fontSize: 10, fontWeight: FontWeight.bold, color: NothingColors.pureWhite),
                  ),
                  GestureDetector(
                    onTap: () {
                      HapticFeedback.lightImpact();
                      setState(() {
                        _eventPickerMonth = DateTime(_eventPickerMonth.year, _eventPickerMonth.month + 1, 1);
                      });
                    },
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 2),
                      child: Icon(Icons.chevron_right_rounded, size: 18, color: NothingColors.muted),
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Weekday headers (7 columns)
          Row(
            children: const ['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((w) {
              return Expanded(
                child: Center(
                  child: Text(
                    w,
                    style: const TextStyle(
                      fontFamily: 'NDot',
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: NothingColors.muted,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 8),

          // Days Grid (Rows of 7 days)
          Builder(
            builder: (context) {
              final List<Widget> rows = [];
              List<Widget> currentRow = [];

              for (int i = 1; i < startingWeekday; i++) {
                currentRow.add(const Expanded(child: SizedBox(height: 34)));
              }

              for (int d = 1; d <= daysInMonth; d++) {
                final dt = DateTime(_eventPickerMonth.year, _eventPickerMonth.month, d);
                final key = DateFormat('yyyy-MM-dd').format(dt);
                final isSelected = _eventRecurrence == EventRecurrence.yearly
                    ? (_selectedDate.month == dt.month && _selectedDate.day == dt.day)
                    : (_eventRecurrence == EventRecurrence.monthly
                        ? (_selectedDate.day == dt.day)
                        : _eventDates.contains(key));

                currentRow.add(
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 2.0, vertical: 2.0),
                      child: GestureDetector(
                        onTap: () {
                          HapticFeedback.selectionClick();
                          setState(() {
                            _selectedDate = dt;
                            if (_eventRecurrence != EventRecurrence.none) {
                              _eventDates = [key];
                            } else {
                              if (isSelected) {
                                _eventDates.remove(key);
                              } else {
                                _eventDates.add(key);
                                _eventDates.sort();
                              }
                            }
                          });
                        },
                        child: Container(
                          height: 32,
                          decoration: BoxDecoration(
                            color: isSelected ? NothingColors.tertiaryCyan : Colors.transparent,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isSelected ? NothingColors.tertiaryCyan : NothingColors.glassBorderSubtle,
                            ),
                          ),
                          child: Center(
                            child: Text(
                              d.toString(),
                              style: TextStyle(
                                fontFamily: 'NDot',
                                fontSize: 11,
                                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                color: isSelected ? NothingColors.pitchBlack : NothingColors.pureWhite,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                );

                if (currentRow.length == 7) {
                  rows.add(Row(children: List.from(currentRow)));
                  currentRow.clear();
                }
              }

              if (currentRow.isNotEmpty) {
                while (currentRow.length < 7) {
                  currentRow.add(const Expanded(child: SizedBox(height: 34)));
                }
                rows.add(Row(children: List.from(currentRow)));
              }

              return Column(children: rows);
            },
          ),
        ],
      ),
    );
  }

  Widget _buildRecurrencePill(String label, EventRecurrence rec) {
    final isSelected = _eventRecurrence == rec;
    return Expanded(
      child: GestureDetector(
        onTap: () {
          HapticFeedback.selectionClick();
          setState(() {
            _eventRecurrence = rec;
            final key = DateFormat('yyyy-MM-dd').format(_selectedDate);
            if (rec != EventRecurrence.none && !_eventDates.contains(key)) {
              _eventDates = [key];
            }
          });
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: isSelected ? NothingColors.tertiaryCyan : Colors.transparent,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Center(
            child: Text(
              label,
              style: NothingTypography.dotMatrix(
                fontSize: 9,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                color: isSelected ? NothingColors.pitchBlack : NothingColors.muted,
                letterSpacing: 0.8,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDayChip(String label, int weekday) {
    final isSelected = _selectedRoutineDays.contains(weekday);
    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        setState(() {
          if (isSelected) {
            _selectedRoutineDays.remove(weekday);
          } else {
            _selectedRoutineDays.add(weekday);
            _selectedRoutineDays.sort();
          }
        });
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        width: 38,
        height: 38,
        decoration: BoxDecoration(
          color: isSelected ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? NothingColors.crimson : NothingColors.glassBorderSubtle,
          ),
        ),
        child: Center(
          child: Text(
            label,
            style: NothingTypography.dotMatrix(
              fontSize: 10,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              color: isSelected ? NothingColors.pureWhite : NothingColors.muted,
            ),
          ),
        ),
      ),
    );
  }
}
