# Glimmer — Birthday Reminder

A modern Android app built with Jetpack Compose in a sleek **neumorphic dark** aesthetic. Never forget a birthday again — with reliable reminders, a home-screen widget, and a genuinely offline, private, ad-free experience.

## Features

### Never miss one
- **Multiple reminders per person** — a week before to shop for a gift, the day of to actually wish them, or any combination of on-the-day / 1 day / 3 days / 1 week before.
- **Configurable reminder time** — pick the time of day reminders fire, once, for everyone.
- **Actionable, deep-linked notifications** — tap a reminder to land straight on that person's profile; **Message**, **Call**, and **Remind Me Later** (3-hour snooze) actions right on the notification.
- **Home-screen widget** — see who's coming up next without opening the app.
- **A "reminder health" banner** on Home whenever notifications or exact alarms are blocked, so a reminder silently failing to fire is never a mystery.

### Getting your people in, fast
- **Import from Contacts** — reviewable, one-tap bulk import of everyone in your address book who has a birthday saved, with automatic duplicate detection.
- **Contact linking** — attach a phone number and photo from a single contact without any permission prompt.
- **Birthdays without a year** — for the many contacts (especially Facebook-era address books) that only ever recorded month and day.
- **A short onboarding flow** on first launch that explains the app, offers to import your contacts, and primes the permissions reminders depend on — with real context, not a random system dialog.

### Built for actually using it
- **Smart Home screen** — Today, Favorites, and This Week / This Month / Later, with sticky section headers, a filter by relationship, sort by date or name, and a milestone badge on the birthdays that round to 18, 21, or a decade.
- **Favorites** — pin the people you care most about to the top.
- **Smart Calendar** — browse any month, see every birthday on a given day, respecting your locale's first day of the week.
- **Customizable wish messages** — a default message template (with `{name}`, `{age}`, `{relationship}` placeholders) and optional overrides per relationship, used everywhere a message gets sent.
- **Quick actions** — text, call, message on WhatsApp, or search for gift ideas, straight from a birthday's profile.
- **Photos** — pick one from your gallery or pull it from the linked contact; falls back to a colored monogram, never a blank circle.
- **Undo everything** — deleting a birthday is instant and one tap, with an Undo snackbar rather than a confirmation dialog standing in the way.

### Actually private
- **100% offline** — no account, no ads, no analytics, no network calls at all.
- **Encrypted at rest** — the local database is SQLCipher-encrypted with a passphrase kept in Android Keystore-backed storage; device backups are disabled outright, so nothing ever leaves the device in any form.
- **Notification content hidden on the lock screen** by default — a reminder naming a specific person isn't something a locked phone should show to anyone who picks it up, unless you opt in.
- Every permission (Contacts, exact alarms, battery-optimization exemption) is requested only where it's used, with an explanation, never silently.

## Tech Stack

- **UI**: Kotlin & Jetpack Compose, with a custom neumorphic design system
- **Widget**: Jetpack Glance
- **Architecture**: MVVM with `StateFlow`/`Flow`
- **Database**: Room, SQLCipher-encrypted
- **Settings**: Jetpack DataStore
- **Navigation**: Type-safe Jetpack Navigation Compose with Kotlin Serialization
- **Background work**: `AlarmManager`, `BroadcastReceiver`, and WorkManager (widget updates)
- **Images**: Coil

## Permissions

| Permission | Why | When it's requested |
| :--- | :--- | :--- |
| `POST_NOTIFICATIONS` | Reminders are notifications | Onboarding's reliability step, or Home's banner |
| `SCHEDULE_EXACT_ALARM` | Reminders fire at the right time, not "eventually" | Same as above |
| `READ_CONTACTS` | Bulk-import birthdays from your address book | Only when you tap Import — never on launch |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Some OEMs (Xiaomi, Oppo, Vivo, Samsung) kill background alarms aggressively | Onboarding's reliability step, opt-in |
| `RECEIVE_BOOT_COMPLETED` | Re-arms reminders after a reboot (`AlarmManager` alarms don't survive one) | Automatic, no prompt |

The single-contact picker used for linking a phone number/photo needs **no permission at all** — it runs entirely inside the system Contacts app.

## How to Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Clone this repository.
2. Open **Android Studio**.
3. Select **Open** and choose the `lumina` directory containing this project.
4. Allow Gradle to sync and download all necessary dependencies.
5. Click **Run** to launch the app on your emulator or connected Android device!

*(Alternatively, to build an APK, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**)*

## Automated Builds (CI/CD)

This project is configured with GitHub Actions. Pushing a version tag (e.g., `v1.0.0`) builds both a Debug APK and an R8-shrunk Release APK; the Debug build is always attached to the workflow's Actions artifacts, while the Release build is only published to the repository's Releases tab once the `RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` repo secrets are configured with a real signing key — an unsigned or debug-signed build is never published as a Release.
