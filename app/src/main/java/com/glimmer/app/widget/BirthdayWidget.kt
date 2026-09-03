package com.glimmer.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.glimmer.app.MainActivity
import com.glimmer.app.data.AppDatabase
import com.glimmer.app.data.Birthday
import com.glimmer.app.data.GLog
import com.glimmer.app.viewmodel.daysUntilBirthday
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * FEAT-06: the vision's "see who's up next … real-time countdowns on my home screen" reads most
 * naturally as an actual home-screen widget — the strongest retention mechanic an offline app can
 * have, since the value is delivered without opening anything. One resizable widget (not the
 * review's two separate size variants) showing up to the next 3 people, which keeps this to a
 * single layout instead of two nearly-identical ones.
 *
 * The neumorphic look is deliberately NOT attempted here — Glance's drawing primitives can't do
 * the soft dual-shadow effect (Modifier.neumorphic), and the review's own guidance is to "design
 * a flat, high-contrast variant rather than fighting it." Reads straight from Room on every
 * compose rather than keeping a separate cached copy via GlanceStateDefinition — a widget updates
 * at most a few times a day (WidgetUpdateWorker), so there's no real cost to just querying fresh.
 */
class BirthdayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val upcoming = try {
            val database = AppDatabase.getDatabase(context)
            val birthdays = database.birthdayDao().getAllBirthdays().first()
            val today = LocalDate.now()
            birthdays
                .map { it to daysUntilBirthday(it, today) }
                .sortedBy { it.second }
                .take(3)
        } catch (t: Throwable) {
            GLog.e("Widget", "Failed to load birthdays for the widget", t)
            emptyList()
        }

        provideContent {
            BirthdayWidgetContent(upcoming)
        }
    }
}

@Composable
private fun BirthdayWidgetContent(entries: List<Pair<Birthday, Int>>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1C1B22))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            "🎂 Glimmer",
            style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                "No upcoming birthdays",
                style = TextStyle(color = ColorProvider(Color(0xFFAAAAAA)), fontSize = 12.sp)
            )
        } else {
            entries.forEach { (birthday, daysUntil) ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        birthday.name,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp),
                        maxLines = 1
                    )
                    Text(
                        widgetDaysLabel(daysUntil),
                        style = TextStyle(color = ColorProvider(Color(0xFFFFA07A)), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

private fun widgetDaysLabel(daysUntil: Int): String = when (daysUntil) {
    0 -> "Today!"
    1 -> "Tomorrow"
    else -> "${daysUntil}d"
}
