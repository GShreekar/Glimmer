package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.BirthdayRepository
import com.example.data.NotificationScheduler
import com.example.data.SettingsRepository
import com.example.ui.GlimmerApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GlimmerViewModel
import com.example.viewmodel.GlimmerViewModelFactory

class MainActivity : ComponentActivity() {

    // Registered directly on the Activity — not inside a screen composable — so the request
    // outlives whatever screen happened to be on top when onCreate ran. The old pattern in
    // AddBirthdayScreen/EditBirthdayScreen launched this from a click handler that immediately
    // navigated back in the same frame, tearing down the composable (and its launcher) before the
    // system dialog's result could ever be delivered.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Either way, the Home screen's banner reflects the real, current permission state. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() — it reads the activity's theme
        // (Theme.Glimmer.Starting) to know how to draw the splash window, then switches to
        // postSplashScreenTheme (Theme.MyApplication) once the first frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channels on startup
        NotificationScheduler.createNotificationChannel(this)

        // Prime the notification permission once, at first launch, rather than from a save
        // button deep in a form. The Home screen banner (see HomeScreen) covers the case where
        // the user denies it here or revokes it later.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val database = AppDatabase.getDatabase(this)
        val repository = BirthdayRepository(database.birthdayDao())
        val settingsRepository = SettingsRepository.getInstance(this)
        val factory = GlimmerViewModelFactory(application, repository, settingsRepository)
        val viewModel = ViewModelProvider(this, factory)[GlimmerViewModel::class.java]

        setContent {
            MyApplicationTheme {
                GlimmerApp(viewModel)
            }
        }
    }
}
