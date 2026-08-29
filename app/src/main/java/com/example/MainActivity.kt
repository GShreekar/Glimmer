package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.BirthdayRepository
import com.example.data.NotificationScheduler
import com.example.ui.GlimmerApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GlimmerViewModel
import com.example.viewmodel.GlimmerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel on startup
        NotificationScheduler.createNotificationChannel(this)

        val database = AppDatabase.getDatabase(this)
        val repository = BirthdayRepository(database.birthdayDao())
        val factory = GlimmerViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[GlimmerViewModel::class.java]

        setContent {
            MyApplicationTheme {
                GlimmerApp(viewModel)
            }
        }
    }
}
