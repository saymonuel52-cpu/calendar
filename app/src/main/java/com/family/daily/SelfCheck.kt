package com.family.daily

import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
import com.family.daily.data.AppDb
import com.family.daily.data.RepeatException
import kotlinx.coroutines.flow.first
import java.io.File

object SelfCheck {
    data class Result(val name: String, val ok: Boolean, val detail: String = "")

    suspend fun run(ctx: Context): List<Result> {
        val r = mutableListOf<Result>()
        val db = AppDb.get(ctx)

        r.add(Result("Устройство", true, Build.MODEL + ", Android " + Build.VERSION.RELEASE))
        try {
            val pm = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val vc = if (Build.VERSION.SDK_INT >= 28) pm.longVersionCode else pm.versionCode.toLong()
            r.add(Result("Версия", true, pm.versionName + " (сборка " + vc + ")"))
        } catch (_: Exception) {}

        try {
            val evs = db.events().between("2000-01-01", "2099-12-31").first()
            val mem = db.members().all().first()
            val notes = db.notes().all().first()
            val sch = db.school().all().first()
            r.add(Result("БД чтение", true, "событий:${evs.size} людей:${mem.size} заметок:${notes.size} расписаний:${sch.size}"))
        } catch (e: Exception) { r.add(Result("БД чтение", false, e.toString())) }

        try {
            db.repeatExceptions().insert(RepeatException(eventId = 0, date = "1999-01-01"))
            db.repeatExceptions().deleteDate("1999-01-01")
            r.add(Result("БД запись", true, "тестовая строка создана и удалена"))
        } catch (e: Exception) { r.add(Result("БД запись", false, e.toString())) }

        val nm = NotificationManagerCompat.from(ctx)
        r.add(Result("Уведомления", nm.areNotificationsEnabled(), if (nm.areNotificationsEnabled()) "разрешены" else "ЗАПРЕЩЕНЫ в системе — пуши не придут"))

        val last = ctx.getSharedPreferences("app", 0).getLong("lastWorker", 0)
        val mins = if (last > 0) (System.currentTimeMillis() - last) / 60000 else -1
        r.add(Result("Фоновый воркер", last > 0 && mins <= 45, if (last > 0) "работал $mins мин назад" else "ещё не запускался (подожди 15 мин)"))

        try {
            Backup.export(db)
            r.add(Result("Бэкап", true, "файл записан в Загрузки"))
        } catch (e: Exception) { r.add(Result("Бэкап", false, e.toString())) }

        try {
            val mem = db.members().all().first()
            val missing = mem.filter { it.photo.isNotBlank() && !File(it.photo).exists() }
            r.add(Result("Фото", missing.isEmpty(), if (missing.isEmpty()) "все файлы на месте" else "ОТСУТСТВУЮТ: ${missing.size}"))
        } catch (e: Exception) { r.add(Result("Фото", false, e.toString())) }

        try {
            val st = StatFs(ctx.filesDir.path)
            val mb = st.availableBlocksLong * st.blockSizeLong / 1048576
            r.add(Result("Место на диске", mb > 50, "свободно $mb МБ"))
        } catch (e: Exception) { r.add(Result("Место на диске", false, e.toString())) }

        try {
            val prefs = ctx.getSharedPreferences("app", 0).all
            r.add(Result("Настройки", true, "параметров: ${prefs.size}"))
        } catch (e: Exception) { r.add(Result("Настройки", false, e.toString())) }

        return r
    }
}
