package com.family.daily.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

data class DayItem(val kind: String, val id: Long, val title: String, val start: String, val end: String,
                   val allDay: Boolean, val categoryId: Long, val silent: Boolean)

class CalendarRepository(private val db: AppDb) {
    fun dayItems(date: String): Flow<List<DayItem>> = combine(
        db.events().onDay(date), db.school().all(), db.templates().all()
    ) { evs, sch, tpl ->
        val dow = dayOfWeek(date)
        val items = mutableListOf<DayItem>()
        evs.forEach { items.add(DayItem("ev", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
        sch.filter { it.enabled && it.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }
            .forEach { items.add(DayItem("school", it.id, "В школе", it.start, it.end, false, 3, true)) }
        tpl.filter { it.dayOfWeek == dow }
            .forEach { items.add(DayItem("tpl", it.id, it.title, it.start, it.end, false, it.categoryId, it.silent)) }
        items.sortedBy { if (it.allDay) "00:00" else it.start }
    }
    private fun dayOfWeek(d: String): Int {
        val p = d.split("-")
        return Calendar.getInstance().apply { set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) }.get(Calendar.DAY_OF_WEEK)
    }
}
