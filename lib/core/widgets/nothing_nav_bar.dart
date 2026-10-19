import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../theme/nothing_theme.dart';
import '../theme/theme_settings_provider.dart';

class NothingNavBar extends ConsumerWidget {
  final int currentIndex;
  final ValueChanged<int> onTabSelected;
  final VoidCallback onNewTaskPressed;

  const NothingNavBar({
    super.key,
    required this.currentIndex,
    required this.onTabSelected,
    required this.onNewTaskPressed,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bottomInset = MediaQuery.of(context).padding.bottom;
    final themeSettings = ref.watch(themeSettingsProvider);
    final isOled = themeSettings.pitchBlackOled;

    const double topRadius = 24.0;

    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: Radius.circular(topRadius)),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 25, sigmaY: 25),
        child: CustomPaint(
          foregroundPainter: const _TopCurvedBorderPainter(
            radius: topRadius,
            color: Color(0x33FFFFFF),
            strokeWidth: 1.0,
          ),
          child: Container(
            decoration: BoxDecoration(
              color: isOled
                  ? Colors.black.withValues(alpha: 0.65)
                  : const Color(0x99181818),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(topRadius)),
            ),
            padding: EdgeInsets.only(
              left: 24,
              right: 24,
              top: 14,
              bottom: bottomInset > 0 ? bottomInset + 10 : 18,
            ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _NavItem(
                icon: Icons.grid_view_rounded,
                isActive: currentIndex == 0,
                onTap: () => onTabSelected(0),
              ),
              _NavItem(
                icon: Icons.calendar_today_rounded,
                isActive: currentIndex == 1,
                onTap: () => onTabSelected(1),
              ),
              // Center Crimson '+' Action Button
              GestureDetector(
                onTap: () {
                  HapticFeedback.lightImpact();
                  onNewTaskPressed();
                },
                child: Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: NothingColors.crimson,
                    boxShadow: [
                      BoxShadow(
                        color: NothingColors.crimson.withValues(alpha: 0.55),
                        blurRadius: 16,
                        spreadRadius: 1,
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.add_rounded,
                      color: NothingColors.pureWhite,
                      size: 28,
                    ),
                  ),
                ),
              ),
              _NavItem(
                icon: Icons.insights_rounded,
                isActive: currentIndex == 2,
                onTap: () => onTabSelected(2),
              ),
              _NavItem(
                icon: Icons.tune_rounded,
                isActive: currentIndex == 3,
                onTap: () => onTabSelected(3),
              ),
            ],
          ),
        ),
      ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final bool isActive;
  final VoidCallback onTap;

  const _NavItem({
    required this.icon,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        onTap();
      },
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: 50,
        height: 50,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: isActive ? NothingColors.crimson.withValues(alpha: 0.22) : Colors.transparent,
        ),
        child: Center(
          child: Icon(
            icon,
            size: 24,
            color: isActive ? NothingColors.pureWhite : NothingColors.muted,
          ),
        ),
      ),
    );
  }
}

class _TopCurvedBorderPainter extends CustomPainter {
  final double radius;
  final Color color;
  final double strokeWidth;

  const _TopCurvedBorderPainter({
    required this.radius,
    required this.color,
    this.strokeWidth = 1.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;

    final halfStroke = strokeWidth / 2;
    const double extendX = 2.0;
    final r = radius - halfStroke;

    final path = Path();
    // Smoothly enter from off-screen left without downward stubs
    path.moveTo(-extendX, radius);
    path.arcTo(
      Rect.fromLTWH(-extendX, halfStroke, 2 * r, 2 * r),
      pi,
      pi / 2,
      false,
    );
    path.lineTo(size.width + extendX - radius, halfStroke);
    path.arcTo(
      Rect.fromLTWH(size.width + extendX - 2 * radius + halfStroke, halfStroke, 2 * r, 2 * r),
      -pi / 2,
      pi / 2,
      false,
    );
    path.lineTo(size.width + extendX, radius);

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant _TopCurvedBorderPainter oldDelegate) {
    return oldDelegate.radius != radius ||
        oldDelegate.color != color ||
        oldDelegate.strokeWidth != strokeWidth;
  }
}
