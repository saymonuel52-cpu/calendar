package com.family.daily.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

data class DayItem(val kind: String, val id: Long, val title: String, val start: String, val end: String,
                   val allDay: Boolean, val categoryId: Long, val silent: Boolean)

fun dayOfWeekOf(date: String): Int {
    val p = date.split("-")
    return Calendar.getInstance().apply { set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) }.get(Calendar.DAY_OF_WEEK)
}

fun occursOn(e: Event, ds: String): Boolean {
    if (e.repeatType == "NONE") return false
    if (ds < e.date) return false
    if (ds == e.date) return false
    return when (e.repeatType) {
        "DAILY" -> true
        "WEEKLY" -> dayOfWeekOf(ds) == dayOfWeekOf(e.date)
        "MONTHLY" -> ds.substring(8) == e.date.substring(8)
        "CUSTOM" -> e.repeatDays.split(",").mapNotNull { it.toIntOrNull() }.contains(dayOfWeekOf(ds))
        else -> false
    }
}

class CalendarRepository(private val db: AppDb) {
    fun dayItems(date: String): Flow<List<DayItem>> = combine(
        db.events().onDay(date), db.school().all(), db.templates().all(), db.events().repeating()
    ) { evs, sch, tpl, reps ->
        val dow = dayOfWeekOf(date)
        val items = mutableListOf<DayItem>()
        evs.forEach { items.add(DayItem("ev", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
        sch.filter { it.enabled && it.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }
            .forEach { items.add(DayItem("school", it.id, "В школе", it.start, it.end, false, 3, true)) }
        tpl.filter { it.dayOfWeek == dow }
            .forEach { items.add(DayItem("tpl", it.id, it.title, it.start, it.end, false, it.categoryId, it.silent)) }
        reps.filter { occursOn(it, date) }
            .forEach { items.add(DayItem("rep", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
        items.sortedBy { if (it.allDay) "00:00" else it.start }
    }
}
