# Glimmer — Birthday Reminder

A beautiful, modern Android application built with Jetpack Compose featuring a sleek **Neumorphic Dark Mode** aesthetic. Never forget a birthday again with reliable local notifications, smart countdowns, and a clean, ad-free experience.

## Features

- **Neumorphic Aesthetic**: Soft shadows, deep dark surfaces, and glowing accents provide a premium, tactile user experience.
- **Reliable Reminders**: Automated local push notifications powered by `AlarmManager`. Choose to be reminded on the day of, 1 day before, 3 days before, or a week in advance!
- **Smart Calendar**: Instantly filter and view upcoming birthdays by month and exact date.
- **Countdowns**: See exactly how many days are left until the next celebration right on your home screen.
- **Quick Actions**: Directly text, call, or search for gift ideas on Google directly from a birthday profile.
- **100% Offline & Private**: All data is securely stored on your device using Android's local Room Database.

## Tech Stack

- **UI**: Kotlin & Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel) with `StateFlow`
- **Database**: Room Database
- **Navigation**: Type-safe Jetpack Navigation using Kotlin Serialization
- **Background Tasks**: `AlarmManager` & `BroadcastReceiver`

## How to Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Clone this repository.
2. Open **Android Studio**.
3. Select **Open** and choose the `lumina` directory containing this project.
4. Allow Gradle to sync and download all necessary dependencies.
5. Click **Run** to launch the app on your emulator or connected Android device!

*(Alternatively, to build an APK, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**)*

## Automated Builds (CI/CD)

This project is configured with GitHub Actions. Whenever a commit is pushed to the `main` branch or a version tag (e.g., `v1.0.0`) is created, a new Debug APK is automatically built and attached to the repository's GitHub Actions / Releases tab.
