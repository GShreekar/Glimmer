package com.glimmer.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glimmer.app.data.GLog
import java.util.concurrent.TimeUnit

/** Recomposes the widget from whatever's in the DB right now — see BirthdayWidget.provideGlance. */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            BirthdayWidget().updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            GLog.e("Widget", "Failed to update the widget", t)
            // A widget staying one day stale is a much smaller problem than a retried worker
            // burning battery on an error that's probably going to recur (e.g. the DB genuinely
            // can't be read right now) — the next scheduled/triggered update will just try again.
            Result.failure()
        }
    }
}

/**
 * FEAT-06: two triggers keep the widget's countdown correct without the user ever opening the
 * app — a daily periodic worker (the "still updates while forgotten" case, since a raw
 * AppWidgetProvider updatePeriodMillis is capped at a much coarser, unreliable interval) and an
 * immediate one-off request after any birthday actually changes (the review's own
 * "updateAppWidgetOnDataChange").
 */
object WidgetScheduler {
    private const val PERIODIC_WORK_NAME = "widget_daily_update"
    private const val DATA_CHANGED_WORK_NAME = "widget_data_changed_update"

    /** Enqueued once, from MainActivity.onCreate — KEEP means later launches are a no-op. */
    fun schedulePeriodicUpdates(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Enqueued from GlimmerViewModel after any insert/update/delete/undo/favorite-toggle. */
    fun requestImmediateUpdate(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DATA_CHANGED_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
