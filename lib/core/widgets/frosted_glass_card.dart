import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../theme/nothing_theme.dart';
import '../theme/theme_settings_provider.dart';
import 'atmospheric_glow_matrix.dart';

/// Frosted Glass Squircle Card in Nothing OS 3.0 aesthetic.
class FrostedGlassCard extends ConsumerWidget {
  final Widget child;
  final double borderRadius;
  final EdgeInsetsGeometry padding;
  final Color backgroundColor;
  final Color borderColor;
  final double borderWidth;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final bool hasCrimsonGlow;
  final double? glowPhaseOffset;
  final double? glowSeed;

  const FrostedGlassCard({
    super.key,
    required this.child,
    this.borderRadius = 24.0,
    this.padding = const EdgeInsets.all(16.0),
    this.backgroundColor = NothingColors.glassSurface,
    this.borderColor = NothingColors.glassBorder,
    this.borderWidth = 1.0,
    this.onTap,
    this.onLongPress,
    this.hasCrimsonGlow = false,
    this.glowPhaseOffset,
    this.glowSeed,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeSettings = ref.watch(themeSettingsProvider);
    final bool enableGlow = themeSettings.atmosphericGlow;

    final effectiveOffset = glowPhaseOffset ??
        (key != null
            ? (key.hashCode.abs() % 100) / 10.0
            : (child.hashCode.abs() % 100) / 10.0);
    final effectiveSeed = glowSeed ??
        (key != null
            ? (key.hashCode.abs() % 17) / 2.0
            : (child.hashCode.abs() % 17) / 2.0);

    Widget innerContent = Padding(
      padding: padding,
      child: child,
    );

    Widget content = Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        boxShadow: hasCrimsonGlow
            ? [
                BoxShadow(
                  color: NothingColors.crimson.withValues(alpha: 0.16),
                  blurRadius: 24,
                  spreadRadius: 1,
                ),
              ]
            : null,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: Container(
            decoration: BoxDecoration(
              color: backgroundColor,
              borderRadius: BorderRadius.circular(borderRadius),
              border: Border.all(
                color: hasCrimsonGlow && borderColor == NothingColors.glassBorder
                    ? NothingColors.crimson.withValues(alpha: 0.35)
                    : borderColor,
                width: borderWidth,
              ),
            ),
            child: Stack(
              children: [
                if (enableGlow)
                  Positioned.fill(
                    child: CustomPaint(
                      painter: AtmosphericGlowPainter(
                        context: context,
                        isEnabled: enableGlow,
                        phaseOffset: effectiveOffset,
                        seed: effectiveSeed,
                      ),
                    ),
                  ),
                innerContent,
              ],
            ),
          ),
        ),
      ),
    );

    if (onTap != null || onLongPress != null) {
      return GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: content,
      );
    }

    return content;
  }
}
