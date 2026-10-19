import 'package:flutter/services.dart';

class GlyphService {
  static const MethodChannel _channel = MethodChannel('com.tiqo.tiqo/glyph');

  /// Triggers standard 3-pulse Glyph notification
  static Future<void> triggerPulse() async {
    try {
      await _channel.invokeMethod('triggerGlyphPulse');
    } catch (_) {
      // Fallback: standard haptic
      HapticFeedback.heavyImpact();
    }
  }

  /// Triggers high-priority 5-pulse emergency Glyph burst
  static Future<void> triggerHighPriorityAlert() async {
    try {
      await _channel.invokeMethod('triggerHighPriorityAlert');
    } catch (_) {
      // Fallback
      HapticFeedback.vibrate();
    }
  }

  /// Triggers custom 3-pulse reminder sequence: short (100ms) - long (260ms) - short (100ms) in < 1s
  static Future<void> triggerReminderPattern() async {
    try {
      await _channel.invokeMethod('triggerReminderPattern');
    } catch (_) {
      HapticFeedback.heavyImpact();
    }
  }

  /// Updates Pomodoro LED countdown bar on hardware (fraction from 1.0 down to 0.0)
  static Future<void> updateProgress(double fraction) async {
    try {
      await _channel.invokeMethod('updateGlyphProgress', {'progress': fraction});
    } catch (_) {}
  }

  /// Shows system notification with action button and triggers reminder sequence
  static Future<void> showNotification({
    required int id,
    required String title,
    required String message,
    String? taskId,
  }) async {
    try {
      await _channel.invokeMethod('showNotification', {
        'id': id,
        'title': title,
        'message': message,
        'taskId': taskId,
      });
    } catch (_) {}
  }

  /// Cancels active system notification
  static Future<void> cancelNotification(int id) async {
    try {
      await _channel.invokeMethod('cancelNotification', {'id': id});
    } catch (_) {}
  }

  /// Sets Nothing Glyph hardware brightness level (0.1 to 1.0)
  static Future<void> setBrightness(double brightness) async {
    try {
      await _channel.invokeMethod('setGlyphBrightness', {'brightness': brightness});
    } catch (_) {}
  }
}
