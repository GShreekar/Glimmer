package com.glimmer.app.data

import android.util.Log
import com.glimmer.app.BuildConfig

/**
 * Section 4.2 (Error handling & logging): the app previously had not one `Log` call anywhere in
 * 3,600+ lines, so "my reminder didn't fire" was undiagnosable — on-device or from a bug report.
 * This is a debug-only logger for routine tracing plus an always-on error logger for genuine
 * failures (DB errors, a boot-time reschedule failing, an alarm falling back to inexact, …), all
 * tagged "Glimmer/<area>" so `adb logcat -s` can filter to just this app's diagnostics.
 *
 * No third-party crash-reporting dependency — that would mean phoning home from an app whose
 * entire pitch is "100% offline & private" (see SEC-01/SEC-02). Log.e output is still visible to
 * `adb logcat` and to Play Console's ANR/crash reports (which read the OS log buffer directly),
 * which is the right amount of diagnostics for an app that makes no network calls.
 */
object GLog {
    private const val TAG_PREFIX = "Glimmer/"

    fun d(area: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG_PREFIX + area, message)
    }

    fun w(area: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG_PREFIX + area, message, throwable)
    }

    fun e(area: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG_PREFIX + area, message, throwable)
    }
}
