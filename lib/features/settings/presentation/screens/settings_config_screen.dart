import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/services/glyph_service.dart';
import '../../../../core/theme/nothing_theme.dart';
import '../../../../core/theme/theme_settings_provider.dart';
import '../../../../core/widgets/frosted_glass_card.dart';
import '../../../../core/widgets/nothing_badge.dart';
import '../../../../core/widgets/nothing_toggle.dart';
import 'package:tiqo/features/tasks/presentation/controllers/task_controller.dart';
import 'package:tiqo/features/tasks/models/task_model.dart';

class SettingsConfigScreen extends ConsumerStatefulWidget {
  const SettingsConfigScreen({super.key});

  @override
  ConsumerState<SettingsConfigScreen> createState() => _SettingsConfigScreenState();
}

class _SettingsConfigScreenState extends ConsumerState<SettingsConfigScreen> with SingleTickerProviderStateMixin {
  bool _isGlyphSimulating = false;
  late AnimationController _glyphAnimController;

  @override
  void initState() {
    super.initState();
    _glyphAnimController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
  }

  @override
  void dispose() {
    _glyphAnimController.dispose();
    super.dispose();
  }

  void _testGlyphSequence() async {
    setState(() => _isGlyphSimulating = true);
    _glyphAnimController.repeat(reverse: true);

    // Triggers hardware Nothing Glyph LEDs and rhythmic haptic feedback (ZERO camera flash)
    await GlyphService.triggerHighPriorityAlert();

    await Future.delayed(const Duration(milliseconds: 1400));
    if (mounted) {
      _glyphAnimController.stop();
      _glyphAnimController.reset();
      setState(() => _isGlyphSimulating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final categories = ref.watch(categoryListProvider);
    final allTasks = ref.watch(taskListProvider);
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
              // Header
              _buildHeader(),
              const SizedBox(height: 20),

              // Section 1: GLYPH INTERFACE WITH LIVE TEST BUTTON
              _buildSectionHeader('GLYPH INTERFACE // HARDWARE'),
              const SizedBox(height: 8),
              FrostedGlassCard(
                borderRadius: 22,
                padding: const EdgeInsets.all(18),
                backgroundColor: NothingColors.surfaceContainerLow,
                child: Column(
                  children: [
                    // Glyph Test Simulator Box
                    _buildGlyphVisualizerBox(themeSettings.glyphBrightness),
                    const SizedBox(height: 16),
                    _buildSettingRow(
                      title: 'GLYPH SYNC',
                      subtitle: 'Synchronize LED feedback with task timers and alerts',
                      value: themeSettings.glyphSync,
                      onChanged: (val) {
                        ref.read(themeSettingsProvider.notifier).setGlyphSync(val);
                        if (val) GlyphService.triggerPulse();
                      },
                    ),
                    const Divider(color: NothingColors.glassBorderSubtle, height: 28),
                    _buildSettingRow(
                      title: 'FLIP TO GLYPH',
                      subtitle: 'Mute sounds and trigger light glyph sequences when face down',
                      value: themeSettings.flipToGlyph,
                      onChanged: (val) {
                        ref.read(themeSettingsProvider.notifier).setFlipToGlyph(val);
                      },
                    ),
                    const Divider(color: NothingColors.glassBorderSubtle, height: 28),
                    _buildBrightnessSlider(themeSettings.glyphBrightness),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Section 2: TASK CATEGORIES MANAGEMENT
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: _buildSectionHeader('TASK CATEGORIES'),
                  ),
                  GestureDetector(
                    onTap: () => _showAddCategoryDialog(context),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
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
                            'NEW CATEGORY',
                            style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.crimson, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              FrostedGlassCard(
                borderRadius: 22,
                padding: const EdgeInsets.all(18),
                backgroundColor: NothingColors.surfaceContainerLow,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'ACTIVE CATEGORIES',
                          style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted),
                        ),
                        Text(
                          '${categories.length} CONFIGURED',
                          style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: categories.map((cat) {
                        return Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                          decoration: BoxDecoration(
                            color: NothingColors.surfaceContainerHigh,
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(color: NothingColors.glassBorderSubtle),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 8,
                                height: 8,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Color(cat.colorValue),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Color(cat.colorValue).withValues(alpha: 0.4),
                                      blurRadius: 6,
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                cat.name,
                                style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.pureWhite, fontWeight: FontWeight.bold),
                              ),
                              if (cat.id.toLowerCase() != 'core' && cat.name.toUpperCase() != 'CORE') ...[
                                const SizedBox(width: 6),
                                GestureDetector(
                                  onTap: () => _confirmDeleteCategory(context, cat, categories, allTasks),
                                  child: Container(
                                    padding: const EdgeInsets.all(2),
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: Colors.white.withValues(alpha: 0.1),
                                    ),
                                    child: const Icon(Icons.close, size: 12, color: NothingColors.muted),
                                  ),
                                ),
                              ] else ...[
                                const SizedBox(width: 6),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                                  decoration: BoxDecoration(
                                    color: NothingColors.crimson.withValues(alpha: 0.2),
                                    borderRadius: BorderRadius.circular(4),
                                  ),
                                  child: Text(
                                    'DEF',
                                    style: NothingTypography.dotMatrix(fontSize: 8, color: NothingColors.crimson, fontWeight: FontWeight.bold),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        );
                      }).toList(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Section 3: STORAGE // SECURITY (Read-Only Informative Card)
              _buildSectionHeader('STORAGE // SECURITY'),
              const SizedBox(height: 8),
              FrostedGlassCard(
                borderRadius: 22,
                padding: const EdgeInsets.all(18),
                backgroundColor: NothingColors.surfaceContainerLow,
                child: Column(
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(9),
                          decoration: BoxDecoration(
                            color: NothingColors.crimson.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: NothingColors.crimson.withValues(alpha: 0.3)),
                          ),
                          child: const Icon(Icons.shield_outlined, color: NothingColors.crimson, size: 22),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'LOCAL DATA VAULT',
                                style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                '100% On-Device Vault • Local Hive Database • Zero Cloud Tracking',
                                style: NothingTypography.sans(fontSize: 11, color: NothingColors.muted),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        const NothingBadge(label: 'OFFLINE', style: BadgeStyle.crimson, fontSize: 9),
                      ],
                    ),
                    const Divider(color: NothingColors.glassBorderSubtle, height: 28),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('DATABASE ENGINE', style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 3),
                            Text('Hive 2.2 local encrypted binary format', style: NothingTypography.sans(fontSize: 11, color: NothingColors.muted)),
                          ],
                        ),
                        Text('LOCAL ENCRYPTED', style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.crimson)),
                      ],
                    ),
                    const Divider(color: NothingColors.glassBorderSubtle, height: 28),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () => _showExportVaultDialog(context),
                            icon: const Icon(Icons.file_upload_outlined, size: 15, color: NothingColors.pureWhite),
                            label: Text(
                              'EXPORT VAULT',
                              style: NothingTypography.dotMatrix(fontSize: 10, fontWeight: FontWeight.bold, color: NothingColors.pureWhite, letterSpacing: 0.8),
                            ),
                            style: OutlinedButton.styleFrom(
                              side: const BorderSide(color: NothingColors.glassBorderSubtle),
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: () => _showImportVaultDialog(context),
                            icon: const Icon(Icons.file_download_outlined, size: 15, color: NothingColors.pitchBlack),
                            label: Text(
                              'IMPORT VAULT',
                              style: NothingTypography.dotMatrix(fontSize: 10, fontWeight: FontWeight.bold, color: NothingColors.pitchBlack, letterSpacing: 0.8),
                            ),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: NothingColors.pureWhite,
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                              elevation: 0,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Section 4: AESTHETIC // DISPLAY (Real Live Theme Toggles)
              _buildSectionHeader('AESTHETIC // DISPLAY'),
              const SizedBox(height: 8),
              FrostedGlassCard(
                borderRadius: 22,
                padding: const EdgeInsets.all(18),
                backgroundColor: NothingColors.surfaceContainerLow,
                child: Column(
                  children: [
                    _buildSettingRow(
                      title: 'DOT-MATRIX TYPOGRAPHY',
                      subtitle: 'NDot retro-matrix monospace for headers, dates, and metrics',
                      value: themeSettings.dotMatrixTypography,
                      onChanged: (val) {
                        HapticFeedback.selectionClick();
                        ref.read(themeSettingsProvider.notifier).setDotMatrix(val);
                      },
                    ),
                    const Divider(color: NothingColors.glassBorderSubtle, height: 28),
                    _buildSettingRow(
                      title: 'PITCH BLACK OLED MODE',
                      subtitle: 'True pitch black (#000000) AMOLED pixels vs graphite slate',
                      value: themeSettings.pitchBlackOled,
                      onChanged: (val) {
                        HapticFeedback.selectionClick();
                        ref.read(themeSettingsProvider.notifier).setPitchBlack(val);
                      },
                    ),
                    const Divider(color: NothingColors.glassBorderSubtle, height: 28),
                    _buildSettingRow(
                      title: 'ATMOSPHERIC GLOW MATRIX',
                      subtitle: 'Four dynamic ambient fireflies drifting behind frosted glass cards',
                      value: themeSettings.atmosphericGlow,
                      onChanged: (val) {
                        HapticFeedback.selectionClick();
                        ref.read(themeSettingsProvider.notifier).setAtmosphericGlow(val);
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Section 5: SYSTEM INFORMATION
              _buildSectionHeader('SYSTEM INFORMATION'),
              const SizedBox(height: 8),
              FrostedGlassCard(
                borderRadius: 22,
                padding: const EdgeInsets.all(18),
                backgroundColor: NothingColors.surfaceContainerLow,
                child: Column(
                  children: [
                    _buildInfoRow('FRAMEWORK', 'FLUTTER 3.47.7 // DART 3.13'),
                    const SizedBox(height: 12),
                    _buildInfoRow('DESIGN SYSTEM', 'NOTHING OS 3.0 // N-MATRIX'),
                    const SizedBox(height: 12),
                    _buildInfoRow('CORE HARDWARE', 'NOTHING PHONE (3A / 2A) / EMULATOR'),
                    const SizedBox(height: 12),
                    _buildInfoRow('GLYPH DRIVER', 'NOTHING GLYPH MATRIX SDK v2.0'),
                  ],
                ),
              ),
              const SizedBox(height: 40),
            ],
          ),
        ),
      ),
    );
  }

  void _showAddCategoryDialog(BuildContext context) {
    HapticFeedback.lightImpact();
    final nameController = TextEditingController();
    int selectedColor = 0xFFE11D2A;

    final colors = [
      0xFFE11D2A, // Crimson
      0xFF00E5FF, // Cyan
      0xFFFFB300, // Amber
      0xFFB388FF, // Purple
      0xFF00E676, // Lime
      0xFFFFFFFF, // Pure White
    ];

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDlgState) {
            return AlertDialog(
              backgroundColor: NothingColors.surfaceContainerLowest,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(24),
                side: const BorderSide(color: NothingColors.glassBorderSubtle),
              ),
              title: Text(
                'NEW CATEGORY',
                style: NothingTypography.dotMatrix(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: nameController,
                    style: NothingTypography.sans(fontSize: 14),
                    textCapitalization: TextCapitalization.characters,
                    decoration: InputDecoration(
                      labelText: 'CATEGORY NAME',
                      labelStyle: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.muted),
                      filled: true,
                      fillColor: NothingColors.surfaceContainer,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text('CHOOSE COLOR MATRIX', style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted)),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: colors.map((col) {
                      final isSel = selectedColor == col;
                      return GestureDetector(
                        onTap: () {
                          HapticFeedback.selectionClick();
                          setDlgState(() => selectedColor = col);
                        },
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: Color(col),
                            border: Border.all(
                              color: isSel ? NothingColors.pureWhite : Colors.transparent,
                              width: 2.5,
                            ),
                            boxShadow: isSel
                                ? [
                                    BoxShadow(
                                      color: Color(col).withValues(alpha: 0.6),
                                      blurRadius: 8,
                                      spreadRadius: 1,
                                    ),
                                  ]
                                : null,
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: Text('CANCEL', style: NothingTypography.dotMatrix(color: NothingColors.muted)),
                ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: NothingColors.crimson,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  onPressed: () {
                    final name = nameController.text.trim();
                    if (name.isNotEmpty) {
                      ref.read(categoryListProvider.notifier).addCategory(name, selectedColor);
                      Navigator.pop(ctx);
                    }
                  },
                  child: Text('CREATE', style: NothingTypography.dotMatrix(color: NothingColors.pureWhite, fontWeight: FontWeight.bold)),
                ),
              ],
            );
          },
        );
      },
    );
  }

  void _confirmDeleteCategory(
    BuildContext context,
    CategoryModel category,
    List<CategoryModel> allCategories,
    List<TaskModel> allTasks,
  ) {
    HapticFeedback.mediumImpact();
    final tasksUsing = allTasks.where((t) => t.category.toUpperCase() == category.name.toUpperCase()).toList();

    if (tasksUsing.isEmpty) {
      showDialog(
        context: context,
        builder: (ctx) {
          return AlertDialog(
            backgroundColor: NothingColors.surfaceContainerLowest,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(24),
              side: const BorderSide(color: NothingColors.glassBorderSubtle),
            ),
            title: Text(
              'DELETE CATEGORY',
              style: NothingTypography.dotMatrix(fontSize: 14, fontWeight: FontWeight.bold),
            ),
            content: Text(
              'Are you sure you want to delete category "${category.name}"? No tasks are assigned to this category.',
              style: NothingTypography.sans(fontSize: 13, color: NothingColors.pureWhite),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: Text('CANCEL', style: NothingTypography.dotMatrix(color: NothingColors.muted)),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: NothingColors.crimson,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                onPressed: () {
                  ref.read(categoryListProvider.notifier).deleteCategory(category.id);
                  Navigator.pop(ctx);
                },
                child: Text('DELETE', style: NothingTypography.dotMatrix(color: NothingColors.pureWhite, fontWeight: FontWeight.bold)),
              ),
            ],
          );
        },
      );
      return;
    }

    // Tasks are assigned to this category: User gets choice to migrate or delete
    final otherCategories = allCategories.where((c) => c.name.toUpperCase() != category.name.toUpperCase()).toList();
    String targetCategory = otherCategories.isNotEmpty ? otherCategories.first.name : 'WORK';
    int deleteAction = 0; // 0: Migrate, 1: Delete tasks

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDlgState) {
            return AlertDialog(
              backgroundColor: NothingColors.surfaceContainerLowest,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(24),
                side: const BorderSide(color: NothingColors.glassBorderSubtle),
              ),
              title: Text(
                'DELETE CATEGORY // ${category.name}',
                style: NothingTypography.dotMatrix(fontSize: 13, fontWeight: FontWeight.bold),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Category "${category.name}" is assigned to ${tasksUsing.length} tasks. Choose an action:',
                    style: NothingTypography.sans(fontSize: 13, color: NothingColors.pureWhite),
                  ),
                  const SizedBox(height: 16),
                  GestureDetector(
                    onTap: () => setDlgState(() => deleteAction = 0),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 18,
                            height: 18,
                            margin: const EdgeInsets.only(top: 2, right: 10),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: deleteAction == 0 ? NothingColors.crimson : NothingColors.muted,
                                width: 2,
                              ),
                            ),
                            child: deleteAction == 0
                                ? Center(
                                    child: Container(
                                      width: 8,
                                      height: 8,
                                      decoration: const BoxDecoration(
                                        shape: BoxShape.circle,
                                        color: NothingColors.crimson,
                                      ),
                                    ),
                                  )
                                : null,
                          ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Move tasks to:',
                                  style: NothingTypography.sans(fontSize: 12, fontWeight: FontWeight.w600),
                                ),
                                if (otherCategories.isNotEmpty)
                                  DropdownButton<String>(
                                    value: targetCategory,
                                    dropdownColor: NothingColors.surfaceContainerHigh,
                                    isExpanded: true,
                                    underline: Container(height: 1, color: NothingColors.glassBorder),
                                    style: NothingTypography.dotMatrix(fontSize: 12, color: NothingColors.pureWhite),
                                    items: otherCategories.map((c) {
                                      return DropdownMenuItem<String>(
                                        value: c.name,
                                        child: Text(c.name),
                                      );
                                    }).toList(),
                                    onChanged: deleteAction == 0
                                        ? (val) {
                                            if (val != null) setDlgState(() => targetCategory = val);
                                          }
                                        : null,
                                  ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  GestureDetector(
                    onTap: () => setDlgState(() => deleteAction = 1),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 18,
                            height: 18,
                            margin: const EdgeInsets.only(top: 2, right: 10),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: deleteAction == 1 ? NothingColors.crimson : NothingColors.muted,
                                width: 2,
                              ),
                            ),
                            child: deleteAction == 1
                                ? Center(
                                    child: Container(
                                      width: 8,
                                      height: 8,
                                      decoration: const BoxDecoration(
                                        shape: BoxShape.circle,
                                        color: NothingColors.crimson,
                                      ),
                                    ),
                                  )
                                : null,
                          ),
                          Expanded(
                            child: Text(
                              'Delete tasks with category (${tasksUsing.length})',
                              style: NothingTypography.sans(fontSize: 12, color: NothingColors.crimson, fontWeight: FontWeight.w600),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: Text('CANCEL', style: NothingTypography.dotMatrix(color: NothingColors.muted)),
                ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: NothingColors.crimson,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  onPressed: () async {
                    if (deleteAction == 0) {
                      await ref.read(taskListProvider.notifier).migrateTasksCategory(category.name, targetCategory);
                    } else {
                      await ref.read(taskListProvider.notifier).deleteTasksByCategory(category.name);
                    }
                    await ref.read(categoryListProvider.notifier).deleteCategory(category.id);
                    if (ctx.mounted) Navigator.pop(ctx);
                  },
                  child: Text('CONFIRM', style: NothingTypography.dotMatrix(color: NothingColors.pureWhite, fontWeight: FontWeight.bold)),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildHeader() {
    return Row(
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
          'SETTINGS',
          style: NothingTypography.dotMatrix(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            letterSpacing: 1.2,
          ),
        ),
      ],
    );
  }

  Widget _buildSectionHeader(String title) {
    return Row(
      children: [
        Container(
          width: 3,
          height: 12,
          color: NothingColors.crimson,
        ),
        const SizedBox(width: 8),
        Text(
          title,
          style: NothingTypography.dotMatrix(
            fontSize: 11,
            color: NothingColors.muted,
            letterSpacing: 1.5,
          ),
        ),
      ],
    );
  }

  Widget _buildGlyphVisualizerBox(double brightness) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: NothingColors.pitchBlack,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: _isGlyphSimulating ? NothingColors.crimson : NothingColors.glassBorderSubtle,
          width: _isGlyphSimulating ? 1.5 : 1.0,
        ),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    width: 7,
                    height: 7,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: _isGlyphSimulating ? NothingColors.crimson : NothingColors.muted,
                      boxShadow: _isGlyphSimulating
                          ? [
                              const BoxShadow(color: NothingColors.crimson, blurRadius: 8, spreadRadius: 2),
                            ]
                          : null,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    _isGlyphSimulating ? 'GLYPH ACTIVE' : 'GLYPH MATRIX HARDWARE',
                    style: NothingTypography.dotMatrix(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: _isGlyphSimulating ? NothingColors.crimson : NothingColors.pureWhite,
                    ),
                  ),
                ],
              ),
              // Compact Test Button
              GestureDetector(
                onTap: _testGlyphSequence,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: NothingColors.crimson,
                    borderRadius: BorderRadius.circular(8),
                    boxShadow: [
                      BoxShadow(
                        color: NothingColors.crimson.withValues(alpha: 0.35),
                        blurRadius: 8,
                      ),
                    ],
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.flash_on_rounded, size: 13, color: NothingColors.pureWhite),
                      const SizedBox(width: 4),
                      Text(
                        'TEST GLYPH',
                        style: NothingTypography.dotMatrix(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: NothingColors.pureWhite,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Visual Glyph Backplate Simulation
          AnimatedBuilder(
            animation: _glyphAnimController,
            builder: (context, _) {
              final glowValue = _isGlyphSimulating
                  ? (_glyphAnimController.value * brightness)
                  : (0.15 * brightness);
              final lightColor = _isGlyphSimulating
                  ? NothingColors.pureWhite
                  : NothingColors.muted.withValues(alpha: 0.3);

              return Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Left C-Ring Glyph
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: lightColor.withValues(alpha: glowValue.clamp(0.2, 1.0)),
                        width: 3,
                      ),
                      boxShadow: _isGlyphSimulating
                          ? [
                              BoxShadow(color: Colors.white.withValues(alpha: glowValue), blurRadius: 10, spreadRadius: 2),
                            ]
                          : null,
                    ),
                  ),
                  const SizedBox(width: 16),
                  // Center Slanted Glyph Strip
                  Container(
                    width: 48,
                    height: 4,
                    decoration: BoxDecoration(
                      color: lightColor.withValues(alpha: glowValue.clamp(0.2, 1.0)),
                      borderRadius: BorderRadius.circular(2),
                      boxShadow: _isGlyphSimulating
                          ? [
                              BoxShadow(color: Colors.white.withValues(alpha: glowValue), blurRadius: 10, spreadRadius: 2),
                            ]
                          : null,
                    ),
                  ),
                  const SizedBox(width: 16),
                  // Bottom Dot Glyph
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: (_isGlyphSimulating ? NothingColors.crimson : NothingColors.muted).withValues(alpha: glowValue.clamp(0.3, 1.0)),
                      boxShadow: _isGlyphSimulating
                          ? [
                              const BoxShadow(color: NothingColors.crimson, blurRadius: 10, spreadRadius: 2),
                            ]
                          : null,
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildSettingRow({
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => onChanged(!value),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold)),
                const SizedBox(height: 3),
                Text(subtitle, style: NothingTypography.sans(fontSize: 11, color: NothingColors.muted)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          NothingToggle(value: value, onChanged: onChanged),
        ],
      ),
    );
  }

  Widget _buildBrightnessSlider(double currentBrightness) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('GLYPH BRIGHTNESS', style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold)),
            Text('${(currentBrightness * 100).round()}%', style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.crimson)),
          ],
        ),
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            trackHeight: 3,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
          ),
          child: Slider(
            value: currentBrightness.clamp(0.1, 1.0),
            min: 0.1,
            max: 1.0,
            activeColor: NothingColors.crimson,
            inactiveColor: NothingColors.surfaceContainerHighest,
            onChanged: (val) {
              ref.read(themeSettingsProvider.notifier).setGlyphBrightness(val);
            },
            onChangeEnd: (val) {
              HapticFeedback.selectionClick();
              GlyphService.triggerPulse();
            },
          ),
        ),
      ],
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.muted)),
        const SizedBox(width: 8),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: NothingTypography.dotMatrix(fontSize: 10, color: NothingColors.pureWhite, fontWeight: FontWeight.bold),
          ),
        ),
      ],
    );
  }

  void _showExportVaultDialog(BuildContext context) {
    HapticFeedback.lightImpact();
    final jsonString = ref.read(taskListProvider.notifier).exportVault();
    final allTasks = ref.read(taskListProvider);
    final allCats = ref.read(categoryListProvider);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return ClipRRect(
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
          child: Container(
            color: const Color(0xFB121212),
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            child: SafeArea(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
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
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(6),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: NothingColors.crimson.withValues(alpha: 0.2),
                            ),
                            child: const Icon(Icons.file_upload_outlined, size: 16, color: NothingColors.crimson),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'EXPORT DATA VAULT',
                            style: NothingTypography.dotMatrix(fontSize: 13, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                          ),
                        ],
                      ),
                      NothingBadge(
                        label: '${allTasks.length} TASKS // ${allCats.length} CATS',
                        style: BadgeStyle.outline,
                        fontSize: 9,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Full local backup in standardized JSON format. Compatible with templates and transfer between devices.',
                    style: NothingTypography.sans(fontSize: 12, color: NothingColors.muted),
                  ),
                  const SizedBox(height: 12),
                  Container(
                    height: 220,
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: NothingColors.surfaceContainerLowest,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: NothingColors.glassBorderSubtle),
                    ),
                    child: SingleChildScrollView(
                      child: SelectableText(
                        jsonString,
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 11,
                          color: NothingColors.muted,
                          height: 1.35,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: () {
                        HapticFeedback.mediumImpact();
                        Clipboard.setData(ClipboardData(text: jsonString));
                        Navigator.pop(ctx);
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            backgroundColor: NothingColors.surfaceContainerHighest,
                            content: Text(
                              'VAULT JSON COPIED TO CLIPBOARD',
                              style: NothingTypography.dotMatrix(fontSize: 11),
                            ),
                            duration: const Duration(seconds: 2),
                          ),
                        );
                      },
                      icon: const Icon(Icons.copy_rounded, size: 16, color: NothingColors.pitchBlack),
                      label: Text(
                        'COPY TO CLIPBOARD',
                        style: NothingTypography.dotMatrix(fontSize: 12, fontWeight: FontWeight.bold, color: NothingColors.pitchBlack, letterSpacing: 1.2),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: NothingColors.pureWhite,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        elevation: 0,
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  void _showImportVaultDialog(BuildContext context) {
    HapticFeedback.lightImpact();
    final jsonController = TextEditingController();
    String? errorMessage;
    bool isProcessing = false;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
              padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
              child: ClipRRect(
                borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
                child: Container(
                  color: const Color(0xFB121212),
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                  child: SafeArea(
                    child: SingleChildScrollView(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
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
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  Container(
                                    padding: const EdgeInsets.all(6),
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: NothingColors.tertiaryCyan.withValues(alpha: 0.2),
                                    ),
                                    child: const Icon(Icons.file_download_outlined, size: 16, color: NothingColors.tertiaryCyan),
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    'IMPORT / RESTORE VAULT',
                                    style: NothingTypography.dotMatrix(fontSize: 13, fontWeight: FontWeight.bold, letterSpacing: 1.2),
                                  ),
                                ],
                              ),
                              GestureDetector(
                                onTap: () async {
                                  HapticFeedback.selectionClick();
                                  final clip = await Clipboard.getData(Clipboard.kTextPlain);
                                  final clipText = clip?.text;
                                  if (clipText != null && clipText.isNotEmpty) {
                                    setModalState(() {
                                      jsonController.text = clipText;
                                      errorMessage = null;
                                    });
                                  }
                                },
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: NothingColors.surfaceContainerHigh,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(color: NothingColors.glassBorderSubtle),
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      const Icon(Icons.paste_rounded, size: 12, color: NothingColors.tertiaryCyan),
                                      const SizedBox(width: 4),
                                      Text(
                                        'PASTE',
                                        style: NothingTypography.dotMatrix(fontSize: 9, fontWeight: FontWeight.bold, color: NothingColors.tertiaryCyan),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Text(
                            'Paste JSON data from another device or template. Choose merge mode to append or restore to overwrite.',
                            style: NothingTypography.sans(fontSize: 12, color: NothingColors.muted),
                          ),
                          const SizedBox(height: 12),
                          Container(
                            height: 160,
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: NothingColors.surfaceContainerLowest,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: errorMessage != null ? NothingColors.crimson : NothingColors.glassBorderSubtle),
                            ),
                            child: TextField(
                              controller: jsonController,
                              maxLines: null,
                              style: const TextStyle(fontFamily: 'monospace', fontSize: 11, color: NothingColors.pureWhite),
                              decoration: const InputDecoration(
                                hintText: 'Paste vault JSON here: {"tasks": [...], "categories": [...]}',
                                hintStyle: TextStyle(fontFamily: 'monospace', fontSize: 11, color: NothingColors.muted),
                                border: InputBorder.none,
                                isDense: true,
                              ),
                            ),
                          ),
                          if (errorMessage != null) ...[
                            const SizedBox(height: 8),
                            Text(
                              errorMessage!,
                              style: NothingTypography.sans(fontSize: 11, color: NothingColors.crimson),
                            ),
                          ],
                          const SizedBox(height: 16),
                          Row(
                            children: [
                              // Option 1: MERGE & APPEND
                              Expanded(
                                child: ElevatedButton(
                                  onPressed: isProcessing
                                      ? null
                                      : () async {
                                          final text = jsonController.text.trim();
                                          if (text.isEmpty) {
                                            setModalState(() => errorMessage = 'Please paste JSON content first.');
                                            return;
                                          }
                                          setModalState(() {
                                            isProcessing = true;
                                            errorMessage = null;
                                          });
                                          try {
                                            final result = await ref.read(taskListProvider.notifier).importVault(text, mergeMode: true);
                                            await ref.read(categoryListProvider.notifier).reloadCategories();
                                            if (context.mounted) {
                                              Navigator.pop(ctx);
                                              ScaffoldMessenger.of(context).showSnackBar(
                                                SnackBar(
                                                  backgroundColor: NothingColors.surfaceContainerHighest,
                                                  content: Text(
                                                    'MERGED: ${result.tasksImported} tasks, ${result.categoriesImported} categories',
                                                    style: NothingTypography.dotMatrix(fontSize: 11),
                                                  ),
                                                ),
                                              );
                                            }
                                          } catch (e) {
                                            setModalState(() {
                                              isProcessing = false;
                                              errorMessage = 'Import failed: $e';
                                            });
                                          }
                                        },
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: NothingColors.tertiaryCyan,
                                    padding: const EdgeInsets.symmetric(vertical: 14),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                                    elevation: 0,
                                  ),
                                  child: Text(
                                    'MERGE & APPEND',
                                    style: NothingTypography.dotMatrix(fontSize: 11, fontWeight: FontWeight.bold, color: NothingColors.pitchBlack, letterSpacing: 0.8),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 10),
                              // Option 2: RESTORE & OVERWRITE
                              Expanded(
                                child: OutlinedButton(
                                  onPressed: isProcessing
                                      ? null
                                      : () async {
                                          final text = jsonController.text.trim();
                                          if (text.isEmpty) {
                                            setModalState(() => errorMessage = 'Please paste JSON content first.');
                                            return;
                                          }
                                          // Prompt confirmation
                                          final confirmed = await showDialog<bool>(
                                            context: context,
                                            builder: (c) => AlertDialog(
                                              backgroundColor: NothingColors.surfaceContainerLowest,
                                              shape: RoundedRectangleBorder(
                                                borderRadius: BorderRadius.circular(20),
                                                side: const BorderSide(color: NothingColors.crimson),
                                              ),
                                              title: Text('OVERWRITE ALL DATA?', style: NothingTypography.dotMatrix(fontSize: 13, fontWeight: FontWeight.bold, color: NothingColors.crimson)),
                                              content: Text(
                                                'This will completely replace all existing tasks with the vault data. This action cannot be undone.',
                                                style: NothingTypography.sans(fontSize: 12),
                                              ),
                                              actions: [
                                                TextButton(onPressed: () => Navigator.pop(c, false), child: Text('CANCEL', style: NothingTypography.dotMatrix(color: NothingColors.muted))),
                                                ElevatedButton(
                                                  style: ElevatedButton.styleFrom(backgroundColor: NothingColors.crimson),
                                                  onPressed: () => Navigator.pop(c, true),
                                                  child: Text('OVERWRITE', style: NothingTypography.dotMatrix(color: NothingColors.pureWhite, fontWeight: FontWeight.bold)),
                                                ),
                                              ],
                                            ),
                                          );
                                          if (confirmed != true) return;

                                          setModalState(() {
                                            isProcessing = true;
                                            errorMessage = null;
                                          });
                                          try {
                                            final result = await ref.read(taskListProvider.notifier).importVault(text, mergeMode: false);
                                            await ref.read(categoryListProvider.notifier).reloadCategories();
                                            if (context.mounted) {
                                              Navigator.pop(ctx);
                                              ScaffoldMessenger.of(context).showSnackBar(
                                                SnackBar(
                                                  backgroundColor: NothingColors.crimson,
                                                  content: Text(
                                                    'RESTORED: ${result.tasksImported} tasks, ${result.categoriesImported} categories',
                                                    style: NothingTypography.dotMatrix(fontSize: 11, color: NothingColors.pureWhite),
                                                  ),
                                                ),
                                              );
                                            }
                                          } catch (e) {
                                            setModalState(() {
                                              isProcessing = false;
                                              errorMessage = 'Restore failed: $e';
                                            });
                                          }
                                        },
                                  style: OutlinedButton.styleFrom(
                                    side: const BorderSide(color: NothingColors.crimson),
                                    padding: const EdgeInsets.symmetric(vertical: 14),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                                  ),
                                  child: Text(
                                    'RESTORE (OVERWRITE)',
                                    style: NothingTypography.dotMatrix(fontSize: 10, fontWeight: FontWeight.bold, color: NothingColors.crimson, letterSpacing: 0.6),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }
}
