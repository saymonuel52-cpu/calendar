package com.family.daily

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.family.daily.data.AppDb
import com.family.daily.data.CalendarRepository
import kotlinx.coroutines.flow.first
import com.family.daily.data.ReminderQueue
import com.family.daily.data.occursOn
import com.family.daily.ui.minutesOf
import com.family.daily.ui.todayStr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationHelper {
    const val CH = "reminders"
    fun ensure(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = ctx.getSystemService(NotificationManager::class.java)
            if (m.getNotificationChannel(CH) == null)
                m.createNotificationChannel(NotificationChannel(CH, "Напоминания", NotificationManager.IMPORTANCE_HIGH))
        }
    }
    fun post(ctx: Context, id: Int, title: String, text: String) {
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        try {
            val db = AppDb.get(applicationContext)
            val pref = applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
            db.reminders().due(System.currentTimeMillis()).forEach { r ->
                NotificationHelper.post(applicationContext, r.id.toInt(), "Напоминание", r.title)
                db.reminders().markFired(r.id)
            }
            val today = todayStr()
            val c = Calendar.getInstance()
            val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            db.events().repeatingList().filter { occursOn(it, today) && !it.allDay && it.start.isNotBlank() }.forEach { e ->
                e.reminders.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { min ->
                    val fire = minutesOf(e.start) - min
                    if (now in fire..minutesOf(e.start)) {
                        val key = "rep" + e.id + "_" + today + "_" + min
                        if (!pref.getBoolean(key, false)) {
                            pref.edit().putBoolean(key, true).apply()
                            NotificationHelper.post(applicationContext, (e.id * 100 + min).toInt(), "Напоминание", e.title + " (повтор) в " + e.start)
                        }
                    }
                }
            }
            if (pref.getBoolean("digestOn", true)) {
                val digestTime = pref.getString("digestTime", "07:30") ?: "07:30"
                val dm = minutesOf(digestTime)
                if (now in dm until dm + 15 && !pref.getBoolean("digestSent_" + today, false)) {
                    pref.edit().putBoolean("digestSent_" + today, true).apply()
                    val items = CalendarRepository(db).dayItems(today).first()
                    if (items.isNotEmpty()) {
                        val text = items.joinToString("\n") { (if (it.allDay) "весь день" else it.start + "–" + it.end) + " " + it.title }
                        NotificationHelper.post(applicationContext, 888, "Доброе утро! План на сегодня", text)
                    }
                }
            }
            pref.all.keys.filter { it.startsWith("rep") && !it.contains(today) }.forEach { pref.edit().remove(it).apply() }
            val hour = c.get(Calendar.HOUR_OF_DAY)
            if (hour >= 20 && !pref.getBoolean("closed_" + today, false) && !pref.getBoolean("closeNotified_" + today, false)) {
                if (db.events().workOn(today).isNotEmpty()) {
                    pref.edit().putBoolean("closeNotified_" + today, true).apply()
                    NotificationHelper.post(applicationContext, 777, "Вечернее закрытие", "Отметь результаты визитов: Работа → Закрытие дня")
                }
            }
        } catch (e: Exception) { return Result.retry() }
        return Result.success()
    }
}

object ReminderScheduler {
    fun start(ctx: Context) {
        try {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "reminders", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build())
        } catch (e: Exception) { Log.e("ReminderScheduler", "start failed", e) }
    }
    private fun minutesOf(hm: String): Int {
        val p = hm.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    suspend fun scheduleFor(db: AppDb, sourceType: String, sourceId: Long, title: String, date: String, time: String, remindersCsv: String) {
        if (remindersCsv.isBlank()) return
        val base = if (time.isNotBlank()) minutesOf(time) else 8 * 60
        val fmt = SimpleDateFormat("yyyy-MM-dd")
        val dayStart = try { fmt.parse(date)?.time ?: return } catch (_: Exception) { return }
        remindersCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { min ->
            db.reminders().insert(ReminderQueue(sourceType = sourceType, sourceId = sourceId, title = title, fireAt = dayStart + (base - min) * 60000L))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.start(c)
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val pref = getSharedPreferences("app", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(if (pref.getBoolean("dark", false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { pref.edit().putString("crash", Log.getStackTraceString(e)).apply() } catch (_: Exception) {}
            default?.uncaughtException(t, e)
        }
        ReminderScheduler.start(this)
    }
}
