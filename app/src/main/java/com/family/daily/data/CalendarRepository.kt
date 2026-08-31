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

fun repOccurs(repeatType: String, repeatDays: String, startDs: String, ds: String): Boolean {
    if (repeatType == "NONE") return false
    if (ds < startDs) return false
    if (ds == startDs) return false
    return when (repeatType) {
        "DAILY" -> true
        "WEEKLY" -> dayOfWeekOf(ds) == dayOfWeekOf(startDs)
        "MONTHLY" -> ds.substring(8) == startDs.substring(8)
        "CUSTOM" -> repeatDays.split(",").mapNotNull { it.toIntOrNull() }.contains(dayOfWeekOf(ds))
        else -> false
    }
}

fun occursOn(e: Event, ds: String): Boolean = repOccurs(e.repeatType, e.repeatDays, e.date, ds)

class CalendarRepository(private val db: AppDb) {
    fun dayItems(date: String): Flow<List<DayItem>> = combine(
        db.events().onDay(date), db.school().all(), db.templates().all(), db.events().repeating(), db.notes().all()
    ) { evs, sch, tpl, reps, notes ->
        val dow = dayOfWeekOf(date)
        val items = mutableListOf<DayItem>()
        evs.forEach { items.add(DayItem("ev", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
        sch.filter { it.enabled && it.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }
            .forEach { items.add(DayItem("school", it.id, "В школе", it.start, it.end, false, 3, true)) }
        tpl.filter { it.dayOfWeek == dow }
            .forEach { items.add(DayItem("tpl", it.id, it.title, it.start, it.end, false, it.categoryId, it.silent)) }
        reps.filter { occursOn(it, date) }
            .forEach { items.add(DayItem("rep", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
        notes.filter { !it.done && it.date.isNotBlank() && it.date <= date && (it.date == date || repOccurs(it.repeatType, it.repeatDays, it.date, date)) }
            .forEach { items.add(DayItem("note", it.id, it.title, it.time.ifBlank { "заметка" }, "", false, 7, true)) }
        items.sortedBy { if (it.allDay) "00:00" else it.start }
    }
}
