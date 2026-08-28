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
import com.family.daily.data.ReminderQueue
import java.text.SimpleDateFormat
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
            db.reminders().due(System.currentTimeMillis()).forEach { r ->
                NotificationHelper.post(applicationContext, r.id.toInt(), "Напоминание", r.title)
                db.reminders().markFired(r.id)
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
