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
        combine(db.events().onDay(date), db.school().all(), db.templates().all(), db.events().repeating(), db.notes().all()) { evs, sch, tpl, reps, notes ->
            Triple(evs, sch, tpl) to Pair(reps, notes)
        },
        db.repeatExceptions().all()
    ) { (evsSchTpl, repsNotes), excepts ->
        val (evs, sch, tpl) = evsSchTpl
        val (reps, notes) = repsNotes
        val dow = dayOfWeekOf(date)
        val items = mutableListOf<DayItem>()
        evs.forEach { e -> items.add(DayItem("ev", e.id, e.title, e.start, e.end, e.allDay, e.categoryId, e.silent)) }
        sch.filter { s -> s.enabled && s.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }
            .forEach { s -> items.add(DayItem("school", s.id, "В школе", s.start, s.end, false, 3, true)) }
        tpl.filter { t -> t.dayOfWeek == dow }
            .forEach { t -> items.add(DayItem("tpl", t.id, t.title, t.start, t.end, false, t.categoryId, t.silent)) }
        reps.filter { r -> occursOn(r, date) && excepts.none { ex -> ex.eventId == r.id && ex.date == date } }
            .forEach { r -> items.add(DayItem("rep", r.id, r.title, r.start, r.end, r.allDay, r.categoryId, r.silent)) }
        notes.filter { n -> !n.done && n.date.isNotBlank() && n.date <= date && (n.date == date || repOccurs(n.repeatType, n.repeatDays, n.date, date)) }
            .forEach { n -> items.add(DayItem("note", n.id, n.text, n.time.ifBlank { "заметка" }, "", false, 7, true)) }
        items.sortedBy { if (it.allDay) "00:00" else it.start }
    }
}
