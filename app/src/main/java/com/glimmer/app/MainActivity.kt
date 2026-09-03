package com.glimmer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.glimmer.app.data.AppDatabase
import com.glimmer.app.data.BirthdayRepository
import com.glimmer.app.data.NotificationScheduler
import com.glimmer.app.data.SettingsRepository
import com.glimmer.app.ui.GlimmerApp
import com.glimmer.app.ui.theme.MyApplicationTheme
import com.glimmer.app.ui.theme.surfaceDark
import com.glimmer.app.viewmodel.GlimmerViewModel
import com.glimmer.app.viewmodel.GlimmerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // SEC-02: AppDatabase.getDatabase() now does real I/O before Room even opens anything —
        // loading SQLCipher's native library, generating/reading the Keystore-backed passphrase,
        // and (existing installs only, once) migrating a plaintext DB to an encrypted one, which
        // for a large database is not instant. Calling that straight from onCreate would block
        // the main thread — worst case an ANR. Keeping the already-installed splash screen up
        // via setKeepOnScreenCondition until it's done is the idiomatic way to bridge that gap
        // without a visible flash to an empty screen while data loads.
        var isDataReady = false
        splashScreen.setKeepOnScreenCondition { !isDataReady }

        // window.statusBarColor (previously set from a SideEffect in MyApplicationTheme) is
        // deprecated and silently ignored once targetSdk reaches 35, where edge-to-edge is
        // enforced app-wide. Passing the style here — always-dark scrim, light icons, since
        // Glimmer is dark-only — is the working equivalent for every API level this app supports.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(surfaceDark.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(surfaceDark.toArgb())
        )

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

        lifecycleScope.launch {
            val database = withContext(Dispatchers.IO) { AppDatabase.getDatabase(this@MainActivity) }
            val repository = BirthdayRepository(database.birthdayDao(), database.reminderDao())
            val settingsRepository = SettingsRepository.getInstance(this@MainActivity)
            val factory = GlimmerViewModelFactory(application, repository, settingsRepository)
            val viewModel = ViewModelProvider(this@MainActivity, factory)[GlimmerViewModel::class.java]

            isDataReady = true
            setContent {
                MyApplicationTheme {
                    GlimmerApp(viewModel)
                }
            }
        }
    }
}
