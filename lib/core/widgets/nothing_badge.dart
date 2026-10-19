import 'package:flutter/material.dart';
import '../theme/nothing_theme.dart';

enum BadgeStyle {
  normal,
  crimson,
  outline,
}

class NothingBadge extends StatelessWidget {
  final String label;
  final BadgeStyle style;
  final Widget? leading;
  final double fontSize;
  final EdgeInsetsGeometry padding;

  const NothingBadge({
    super.key,
    required this.label,
    this.style = BadgeStyle.normal,
    this.leading,
    this.fontSize = 11.0,
    this.padding = const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
  });

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color textColor;
    Border? border;

    switch (style) {
      case BadgeStyle.crimson:
        bg = NothingColors.crimson;
        textColor = NothingColors.pureWhite;
        break;
      case BadgeStyle.outline:
        bg = Colors.transparent;
        textColor = NothingColors.muted;
        border = Border.all(color: NothingColors.glassBorder);
        break;
      case BadgeStyle.normal:
        bg = NothingColors.surfaceContainerHigh;
        textColor = NothingColors.muted;
        break;
    }

    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
        border: border,
        boxShadow: style == BadgeStyle.crimson
            ? [
                BoxShadow(
                  color: NothingColors.crimson.withValues(alpha: 0.4),
                  blurRadius: 8,
                ),
              ]
            : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (leading != null) ...[
            leading!,
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: NothingTypography.dotMatrix(
              fontSize: fontSize,
              fontWeight: FontWeight.w600,
              color: textColor,
              letterSpacing: 0.8,
            ),
          ),
        ],
      ),
    );
  }
}
