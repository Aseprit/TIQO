import 'package:flutter/material.dart';
import '../theme/nothing_theme.dart';

/// Mechanical Nothing OS Toggle switch with LED-style tactile dot.
class NothingToggle extends StatelessWidget {
  final bool value;
  final ValueChanged<bool> onChanged;

  const NothingToggle({
    super.key,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => onChanged(!value),
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeInOut,
        width: 48,
        height: 28,
        padding: const EdgeInsets.all(2),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(999),
          color: value ? NothingColors.crimson : NothingColors.surfaceContainerHigh,
          boxShadow: value
              ? [
                  BoxShadow(
                    color: NothingColors.crimson.withValues(alpha: 0.45),
                    blurRadius: 10,
                    spreadRadius: 1,
                  ),
                ]
              : null,
        ),
        alignment: value ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          width: 24,
          height: 24,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            color: NothingColors.pureWhite,
          ),
          child: Center(
            child: Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: value ? NothingColors.crimson : NothingColors.muted,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
