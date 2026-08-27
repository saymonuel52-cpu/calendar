package com.family.daily

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.family.daily.data.AppDb
import com.family.daily.data.ReminderQueue
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
        val db = AppDb.get(applicationContext)
        db.reminders().due(System.currentTimeMillis()).forEach { r ->
            NotificationHelper.post(applicationContext, r.id.toInt(), "Напоминание", r.title)
            db.reminders().markFired(r.id)
        }
        return Result.success()
    }
}

object ReminderScheduler {
    fun start(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build())
    }
    fun minutesOf(hm: String): Int { val p = hm.split(":"); return (p[0].toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }
    suspend fun scheduleFor(db: AppDb, sourceType: String, sourceId: Long, title: String, date: String, time: String, remindersCsv: String) {
        if (remindersCsv.isBlank()) return
        val base = if (time.isNotBlank()) minutesOf(time) else 8 * 60
        val dayStart = java.text.SimpleDateFormat("yyyy-MM-dd").parse(date)?.time ?: return
        remindersCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { min ->
            db.reminders().insert(ReminderQueue(sourceType, sourceId, title, dayStart + (base - min) * 60000L))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.start(c)
    }
}

class App : android.app.Application() {
    override fun onCreate() { super.onCreate(); ReminderScheduler.start(this) }
}
