import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import '../theme/nothing_theme.dart';

class AtmosphericGlowEngine {
  static final ValueNotifier<double> phase = ValueNotifier<double>(0.0);
  static Ticker? _ticker;

  static void start(TickerProvider vsync) {
    if (_ticker != null) return;
    _ticker = vsync.createTicker((elapsed) {
      // High-precision microsecond harmonic loop of exactly 16.0 seconds.
      // Integer microsecond modulo eliminates any floating-point time drift.
      const loopDurationMicros = 16000000; // 16.0s
      final progress = (elapsed.inMicroseconds % loopDurationMicros) / loopDurationMicros;
      phase.value = progress * 2.0 * pi;
    });
    _ticker?.start();
  }

  static void stop() {
    _ticker?.stop();
    _ticker?.dispose();
    _ticker = null;
  }
}

class AtmosphericGlowPainter extends CustomPainter {
  final BuildContext context;
  final bool isEnabled;
  final double? phaseOffset;
  final double? seed;

  AtmosphericGlowPainter({
    required this.context,
    required this.isEnabled,
    this.phaseOffset,
    this.seed,
  }) : super(repaint: AtmosphericGlowEngine.phase);

  @override
  void paint(Canvas canvas, Size size) {
    if (!isEnabled || size.width <= 0 || size.height <= 0) return;

    final w = size.width;
    final h = size.height;
    final t = AtmosphericGlowEngine.phase.value;
    final localPhase = t + (phaseOffset ?? 0.0);
    final seedVal = seed ?? (phaseOffset ?? 0.0);

    // All fireflies ("świetliki") are drawn strictly within the card's local coordinate system.
    // Enriched sizing (130-160px for tall cards, 110-135px for month grids, 44-58px for tasks)
    // provides an ambient, ethereal warmth while the 9-stop asymptotic Gaussian curve
    // ensures the perimeter dissolves to 0.0 alpha with zero visible border clipping.
    // Pure 1st harmonic (integer frequency = 1) trigonometric equations ensure a 100% seamless 16s loop.

    if (h >= 600) {
      // Tall card (e.g. Hourly Schedule Timeline)
      // 4 distinct, organic, asymmetrical fireflies drifting along the 24h timeline.
      
      // 1. Early Morning (approx 06:00, left-leaning sweep)
      final orb1Center = Offset(
        w * 0.32 + 38.0 * sin(localPhase),
        h * 0.20 + 26.0 * cos(localPhase),
      );
      const orb1Radius = 145.0;

      // 2. Midday (approx 12:00, right-leaning grand glow)
      final orb2Center = Offset(
        w * 0.70 + 32.0 * cos(localPhase + 1.8),
        h * 0.44 + 36.0 * sin(localPhase + 1.2),
      );
      const orb2Radius = 160.0;

      // 3. Late Afternoon / Evening (approx 17:30, center-left diagonal)
      final orb3Center = Offset(
        w * 0.38 + 42.0 * sin(localPhase + 3.4),
        h * 0.68 + 28.0 * cos(localPhase + 2.7),
      );
      const orb3Radius = 140.0;

      // 4. Night (approx 22:00, lower-right tilted sweep)
      final orb4Center = Offset(
        w * 0.64 + 34.0 * cos(localPhase + 4.9),
        h * 0.88 + 24.0 * sin(localPhase + 4.2),
      );
      const orb4Radius = 150.0;

      _drawOrb(canvas, orb1Center, orb1Radius, 0.17);
      _drawOrb(canvas, orb2Center, orb2Radius, 0.16);
      _drawOrb(canvas, orb3Center, orb3Radius, 0.16);
      _drawOrb(canvas, orb4Center, orb4Radius, 0.17);
    } else if (h >= 120) {
      // Medium card (e.g. Month Grid, Today's Progress, Settings)
      if (h >= 260) {
        // Month Grid & Large analytics cards: 3 rich asymmetrical fireflies
        final orb1Center = Offset(
          w * 0.30 + 28.0 * sin(localPhase),
          h * 0.30 + 18.0 * cos(localPhase),
        );
        final orb2Center = Offset(
          w * 0.70 + 26.0 * cos(localPhase + 2.2),
          h * 0.68 + 16.0 * sin(localPhase + 1.6),
        );
        final orb3Center = Offset(
          w * 0.50 + 32.0 * sin(localPhase + 4.1),
          h * 0.48 + 14.0 * cos(localPhase + 3.7),
        );

        _drawOrb(canvas, orb1Center, 135.0, 0.18);
        _drawOrb(canvas, orb2Center, 125.0, 0.16);
        _drawOrb(canvas, orb3Center, 110.0, 0.14);
      } else {
        // Compact summary cards (e.g. Today's Progress, h ~ 180)
        final orb1Center = Offset(
          w * 0.34 + 26.0 * sin(localPhase),
          h * 0.42 + 16.0 * cos(localPhase),
        );
        final orb2Center = Offset(
          w * 0.68 + 24.0 * cos(localPhase + 2.4),
          h * 0.58 + 14.0 * sin(localPhase + 1.8),
        );

        _drawOrb(canvas, orb1Center, 110.0, 0.18);
        _drawOrb(canvas, orb2Center, 100.0, 0.16);
      }
    } else {
      // Small card (e.g. Task item row, h < 120)
      // Highly individualized, asymmetrical firefly per task item so tasks never move in lockstep
      final radius = (h * 0.68 + ((seedVal * 7.3).abs() % 16.0)).clamp(44.0, 58.0);
      final centerX = w * (0.28 + (((seedVal * 0.47).abs()) % 0.46));
      final centerY = h * (0.45 + (((seedVal * 0.23).abs()) % 0.12));
      final driftX = (18.0 + (((seedVal * 3.7).abs()) % 14.0)) * cos(localPhase);
      final driftY = (4.0 + (((seedVal * 2.1).abs()) % 6.0)) * sin(localPhase + 0.9);

      final orbCenter = Offset(centerX + driftX, centerY + driftY);
      _drawOrb(canvas, orbCenter, radius, 0.14);
    }
  }

  void _drawOrb(Canvas canvas, Offset center, double radius, double peakAlpha) {
    // 1. BlendMode.screen ensures that overlapping orbs add light rather than darken each other.
    // 2. Every color stop uses NothingColors.crimson (RGB: 225, 29, 42) down to alpha: 0.0,
    //    preventing any dirty black fringe or dark destructive interference rings.
    // 3. 9-stop Gaussian asymptotic curve dissolves to 0.0 alpha with zero slope at the outer radius,
    //    guaranteeing no visible edge, stepped rings, or clipping.
    final paint = Paint()
      ..isAntiAlias = true
      ..blendMode = BlendMode.screen
      ..shader = RadialGradient(
        colors: [
          NothingColors.crimson.withValues(alpha: peakAlpha),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.92),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.76),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.54),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.34),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.18),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.08),
          NothingColors.crimson.withValues(alpha: peakAlpha * 0.02),
          NothingColors.crimson.withValues(alpha: 0.0),
        ],
        stops: const [0.0, 0.12, 0.25, 0.40, 0.55, 0.70, 0.82, 0.92, 1.0],
      ).createShader(Rect.fromCircle(center: center, radius: radius));

    canvas.drawCircle(center, radius, paint);
  }

  @override
  bool shouldRepaint(AtmosphericGlowPainter oldDelegate) {
    return oldDelegate.isEnabled != isEnabled;
  }
}
