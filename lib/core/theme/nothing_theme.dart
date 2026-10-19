import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Design tokens and styling for Nothing OS 3.0+ aesthetic.
class NothingColors {
  NothingColors._();

  static const Color pitchBlack = Color(0xFF000000);
  static const Color slateBlack = Color(0xFF0E1013);
  static const Color deepOnyx = Color(0xFF0B0B0B);
  static const Color background = Color(0xFF131313);
  static const Color surfaceContainerLowest = Color(0xFF0E0E0E);
  static const Color surfaceContainerLow = Color(0xFF1C1B1B);
  static const Color surfaceContainer = Color(0xFF201F1F);
  static const Color surfaceContainerHigh = Color(0xFF2A2A2A);
  static const Color surfaceContainerHighest = Color(0xFF353534);
  static const Color surfaceBright = Color(0xFF3A3939);

  // Iconic Nothing Crimson Red
  static const Color crimson = Color(0xFFE11D2A);
  static const Color crimsonDim = Color(0xFFFFB3AD);
  static const Color crimsonGlow = Color(0x40E11D2A);

  // Translucent Frosted Glass
  static const Color glassSurface = Color(0x14FFFFFF); // 8% white
  static const Color glassSurfaceLight = Color(0x24FFFFFF); // 14% white
  static const Color glassBorder = Color(0x26FFFFFF); // 15% white
  static const Color glassBorderSubtle = Color(0x12FFFFFF); // 7% white

  // Text & Accents
  static const Color pureWhite = Color(0xFFFFFFFF);
  static const Color muted = Color(0xFF8E8E93);
  static const Color tertiaryCyan = Color(0xFF7CD0FF);

  static Color getBackgroundColor(bool pitchBlackMode) {
    return pitchBlackMode ? pitchBlack : slateBlack;
  }
}

class NothingTypography {
  NothingTypography._();

  // Dot Matrix / Tech Header style
  static TextStyle dotMatrix({
    double fontSize = 14,
    FontWeight fontWeight = FontWeight.w600,
    Color color = NothingColors.pureWhite,
    double letterSpacing = 1.0,
    bool isDotMatrix = true,
  }) {
    if (!isDotMatrix) {
      try {
        return GoogleFonts.inter(
          fontSize: fontSize,
          fontWeight: fontWeight,
          color: color,
          letterSpacing: letterSpacing * 0.7,
        );
      } catch (_) {
        return TextStyle(
          fontFamily: 'sans-serif',
          fontSize: fontSize,
          fontWeight: fontWeight,
          color: color,
          letterSpacing: letterSpacing * 0.7,
        );
      }
    }

    try {
      return GoogleFonts.spaceMono(
        fontSize: fontSize,
        fontWeight: fontWeight,
        color: color,
        letterSpacing: letterSpacing,
      );
    } catch (_) {
      return TextStyle(
        fontFamily: 'monospace',
        fontSize: fontSize,
        fontWeight: fontWeight,
        color: color,
        letterSpacing: letterSpacing,
      );
    }
  }

  // Clean Sans-Serif Body style
  static TextStyle sans({
    double fontSize = 14,
    FontWeight fontWeight = FontWeight.w400,
    Color color = NothingColors.pureWhite,
    double? height,
    double letterSpacing = 0.1,
  }) {
    try {
      return GoogleFonts.inter(
        fontSize: fontSize,
        fontWeight: fontWeight,
        color: color,
        height: height,
        letterSpacing: letterSpacing,
      );
    } catch (_) {
      return TextStyle(
        fontFamily: 'sans-serif',
        fontSize: fontSize,
        fontWeight: fontWeight,
        color: color,
        height: height,
        letterSpacing: letterSpacing,
      );
    }
  }
}

class NothingTheme {
  NothingTheme._();

  static ThemeData getTheme({bool pitchBlack = true}) {
    final bg = NothingColors.getBackgroundColor(pitchBlack);
    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: bg,
      primaryColor: NothingColors.crimson,
      colorScheme: const ColorScheme.dark(
        primary: NothingColors.crimson,
        secondary: NothingColors.muted,
        surface: NothingColors.surfaceContainer,
        error: NothingColors.crimson,
        onPrimary: NothingColors.pureWhite,
        onSecondary: NothingColors.pureWhite,
        onSurface: NothingColors.pureWhite,
      ),
      fontFamily: GoogleFonts.inter().fontFamily,
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: NothingColors.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
        ),
        elevation: 0,
      ),
    );
  }

  static ThemeData get darkTheme => getTheme(pitchBlack: true);
}
