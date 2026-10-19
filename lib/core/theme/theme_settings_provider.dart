import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import '../services/glyph_service.dart';

class ThemeSettings {
  final bool pitchBlackOled;
  final bool dotMatrixTypography;
  final double glyphBrightness;
  final bool glyphSync;
  final bool flipToGlyph;
  final bool atmosphericGlow;

  const ThemeSettings({
    this.pitchBlackOled = true,
    this.dotMatrixTypography = true,
    this.glyphBrightness = 0.8,
    this.glyphSync = true,
    this.flipToGlyph = true,
    this.atmosphericGlow = true,
  });

  ThemeSettings copyWith({
    bool? pitchBlackOled,
    bool? dotMatrixTypography,
    double? glyphBrightness,
    bool? glyphSync,
    bool? flipToGlyph,
    bool? atmosphericGlow,
  }) {
    return ThemeSettings(
      pitchBlackOled: pitchBlackOled ?? this.pitchBlackOled,
      dotMatrixTypography: dotMatrixTypography ?? this.dotMatrixTypography,
      glyphBrightness: glyphBrightness ?? this.glyphBrightness,
      glyphSync: glyphSync ?? this.glyphSync,
      flipToGlyph: flipToGlyph ?? this.flipToGlyph,
      atmosphericGlow: atmosphericGlow ?? this.atmosphericGlow,
    );
  }
}

class ThemeSettingsNotifier extends StateNotifier<ThemeSettings> {
  static const String _boxName = 'theme_settings_box';
  Box? _box;

  ThemeSettingsNotifier() : super(const ThemeSettings()) {
    _load();
  }

  Future<void> _load() async {
    try {
      if (!Hive.isBoxOpen(_boxName)) {
        _box = await Hive.openBox(_boxName);
      } else {
        _box = Hive.box(_boxName);
      }
      final oled = _box?.get('pitchBlackOled', defaultValue: true) as bool? ?? true;
      final dot = _box?.get('dotMatrixTypography', defaultValue: true) as bool? ?? true;
      final brightness = (_box?.get('glyphBrightness', defaultValue: 0.8) as num?)?.toDouble() ?? 0.8;
      final sync = _box?.get('glyphSync', defaultValue: true) as bool? ?? true;
      final flip = _box?.get('flipToGlyph', defaultValue: true) as bool? ?? true;
      final glow = _box?.get('atmosphericGlow', defaultValue: true) as bool? ?? true;

      state = ThemeSettings(
        pitchBlackOled: oled,
        dotMatrixTypography: dot,
        glyphBrightness: brightness,
        glyphSync: sync,
        flipToGlyph: flip,
        atmosphericGlow: glow,
      );

      // Immediately synchronize hardware brightness on boot
      await GlyphService.setBrightness(brightness);
    } catch (_) {}
  }

  Future<void> setPitchBlack(bool value) async {
    state = state.copyWith(pitchBlackOled: value);
    try {
      await _box?.put('pitchBlackOled', value);
    } catch (_) {}
  }

  Future<void> setDotMatrix(bool value) async {
    state = state.copyWith(dotMatrixTypography: value);
    try {
      await _box?.put('dotMatrixTypography', value);
    } catch (_) {}
  }

  Future<void> setGlyphBrightness(double value) async {
    state = state.copyWith(glyphBrightness: value);
    try {
      await _box?.put('glyphBrightness', value);
      await GlyphService.setBrightness(value);
    } catch (_) {}
  }

  Future<void> setGlyphSync(bool value) async {
    state = state.copyWith(glyphSync: value);
    try {
      await _box?.put('glyphSync', value);
    } catch (_) {}
  }

  Future<void> setFlipToGlyph(bool value) async {
    state = state.copyWith(flipToGlyph: value);
    try {
      await _box?.put('flipToGlyph', value);
    } catch (_) {}
  }

  Future<void> setAtmosphericGlow(bool value) async {
    state = state.copyWith(atmosphericGlow: value);
    try {
      await _box?.put('atmosphericGlow', value);
    } catch (_) {}
  }
}

final themeSettingsProvider = StateNotifierProvider<ThemeSettingsNotifier, ThemeSettings>((ref) {
  return ThemeSettingsNotifier();
});
