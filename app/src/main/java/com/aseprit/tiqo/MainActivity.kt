package com.aseprit.tiqo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.core.designsystem.theme.TiqoTheme
import com.aseprit.tiqo.data.settings.AppSettings
import com.aseprit.tiqo.data.settings.ThemeMode
import com.aseprit.tiqo.notification.EXTRA_OCCURRENCE_DATE
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as TiqoApplication).container
            val appSettings by container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            val darkTheme = when (appSettings.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            TiqoTheme(
                darkTheme = darkTheme,
                accent = Color(appSettings.accent.argb),
                glassIntensity = appSettings.glassIntensity,
                animationsEnabled = appSettings.animationsEnabled,
            ) {
                TiqoApp(
                    container = container,
                    initialDate = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)
                        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
                        ?: LocalDate.now(),
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as TiqoApplication).container.modelManager.onAppForegrounded()
    }

    override fun onStop() {
        (application as TiqoApplication).container.modelManager.onAppBackgrounded()
        super.onStop()
    }
}
