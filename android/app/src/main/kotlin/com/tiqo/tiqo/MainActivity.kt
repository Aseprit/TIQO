package com.tiqo.tiqo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.tiqo.tiqo/glyph"
    private var glyphManager: GlyphManager? = null
    private var isGlyphConnected = false
    private var glyphBrightness: Double = 0.8

    private val glyphCallback = object : GlyphManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            try {
                val targetDevice = when {
                    Common.is24111() -> Glyph.DEVICE_24111
                    Common.is23111() -> Glyph.DEVICE_23111
                    Common.is23113() -> Glyph.DEVICE_23113
                    Common.is22111() -> Glyph.DEVICE_22111
                    Common.is20111() -> Glyph.DEVICE_20111
                    else -> Glyph.DEVICE_23111
                }
                glyphManager?.register(targetDevice)
                glyphManager?.openSession()
                isGlyphConnected = true
            } catch (_: Exception) {
                isGlyphConnected = false
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            isGlyphConnected = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Spotify-style transparent edge-to-edge system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // Initialize Nothing Glyph Manager
        try {
            glyphManager = GlyphManager.getInstance(applicationContext)
            glyphManager?.init(glyphCallback)
        } catch (_: Exception) {
            isGlyphConnected = false
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "triggerGlyphPulse" -> {
                    triggerGlyphSequence(times = 3, intervalMs = 120)
                    result.success(true)
                }
                "triggerHighPriorityAlert" -> {
                    triggerGlyphSequence(times = 5, intervalMs = 80)
                    result.success(true)
                }
                "triggerReminderPattern" -> {
                    triggerReminderAlertPattern()
                    result.success(true)
                }
                "setGlyphBrightness" -> {
                    val b = call.argument<Double>("brightness") ?: 0.8
                    glyphBrightness = b.coerceIn(0.1, 1.0)
                    result.success(true)
                }
                "updateGlyphProgress" -> {
                    val progress = call.argument<Double>("progress") ?: 0.0
                    updateGlyphProgress(progress)
                    result.success(true)
                }
                "showNotification" -> {
                    val id = call.argument<Int>("id") ?: 1001
                    val title = call.argument<String>("title") ?: "Task Reminder"
                    val message = call.argument<String>("message") ?: "Time for your scheduled task"
                    val taskId = call.argument<String>("taskId")
                    showNotification(id, title, message, taskId)
                    result.success(true)
                }
                "cancelNotification" -> {
                    val id = call.argument<Int>("id") ?: 1001
                    cancelNotification(id)
                    result.success(true)
                }
                "isGlyphSupported" -> {
                    result.success(isGlyphConnected)
                }
                else -> result.notImplemented()
            }
        }
    }

    private val NOTIFICATION_CHANNEL_ID = "tiqo_reminders"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Tiqo Task Reminders"
            val descriptionText = "Notifications for scheduled tasks and Pomodoro sessions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 70, 260, 70, 100)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(id: Int, title: String, message: String, taskId: String?) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("taskId", taskId)
            putExtra("action", "START_POMODORO")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.mipmap.ic_launcher, "START POMODORO", pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())

        triggerReminderAlertPattern()
    }

    private fun cancelNotification(id: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    private fun updateGlyphProgress(fraction: Double) {
        if (isGlyphConnected && glyphManager != null) {
            try {
                val progressPercent = (fraction.coerceIn(0.0, 1.0) * 100).toInt()
                val builder = glyphManager?.glyphFrameBuilder ?: GlyphFrame.Builder()
                val frame = builder.buildChannelC().build()
                glyphManager?.displayProgress(frame, progressPercent, true)
            } catch (_: Exception) {}
        }
    }

    private fun triggerReminderAlertPattern() {
        // Pattern: 3 pulses in < 1 second:
        // Flash 1 (short: 100ms) -> pause (70ms) -> Flash 2 (longer: 260ms) -> pause (70ms) -> Flash 3 (short: 100ms)
        val timings = longArrayOf(0, 100, 70, 260, 70, 100)
        val amplitudes = intArrayOf(0, 240, 0, 255, 0, 240)
        vibrateCustom(timings, amplitudes)

        if (isGlyphConnected && glyphManager != null) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val lightLevel = (glyphBrightness * 4000).toInt().coerceIn(200, 4000)
                    val builder = glyphManager?.glyphFrameBuilder ?: GlyphFrame.Builder()
                    val initialFrame = builder.buildChannelA().buildChannelB().buildChannelC().build()
                    val rawChannels = initialFrame.channel
                    val customBuilder = glyphManager?.glyphFrameBuilder ?: GlyphFrame.Builder()
                    for (i in rawChannels.indices) {
                        if (rawChannels[i] > 0) {
                            customBuilder.buildChannel(i, lightLevel)
                        }
                    }
                    val frameAll = customBuilder.build()

                    // Pulse 1: 100ms
                    glyphManager?.toggle(frameAll)
                    delay(100)
                    glyphManager?.turnOff()
                    delay(70)

                    // Pulse 2: 260ms
                    glyphManager?.toggle(frameAll)
                    delay(260)
                    glyphManager?.turnOff()
                    delay(70)

                    // Pulse 3: 100ms
                    glyphManager?.toggle(frameAll)
                    delay(100)
                    glyphManager?.turnOff()
                } catch (_: Exception) {
                    try {
                        triggerGlyphSequence(times = 3, intervalMs = 90)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun vibrateCustom(timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val effect = VibrationEffect.createWaveform(timings, -1)
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    private fun triggerGlyphSequence(times: Int, intervalMs: Int) {
        // Haptic feedback for tactile feel
        vibratePattern(times)

        // Hardware Glyph LED control
        if (isGlyphConnected && glyphManager != null) {
            try {
                val lightLevel = (glyphBrightness * 4000).toInt().coerceIn(200, 4000)
                val builder = glyphManager?.glyphFrameBuilder ?: GlyphFrame.Builder()
                val initialFrame = builder
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .build()
                val rawChannels = initialFrame.channel
                val customBuilder = glyphManager?.glyphFrameBuilder ?: GlyphFrame.Builder()
                for (i in rawChannels.indices) {
                    if (rawChannels[i] > 0) {
                        customBuilder.buildChannel(i, lightLevel)
                    }
                }
                val frame = customBuilder
                    .buildPeriod(intervalMs * 2)
                    .buildCycles(times)
                    .buildInterval(intervalMs)
                    .build()
                glyphManager?.animate(frame)
            } catch (_: Exception) {
                // If animation fails, ensure no crashes
            }
        }
    }

    private fun vibratePattern(times: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val timings = LongArray(times * 2) { if (it % 2 == 0) 50L else 70L }
                val amplitudes = IntArray(times * 2) { if (it % 2 == 0) 220 else 0 }
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val timings = LongArray(times * 2) { if (it % 2 == 0) 50L else 70L }
                val effect = VibrationEffect.createWaveform(timings, -1)
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            if (isGlyphConnected) {
                glyphManager?.closeSession()
            }
            glyphManager?.unInit()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
