package com.family.daily

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.family.daily.data.AppDb
import com.family.daily.data.CalendarRepository
import com.family.daily.ui.todayStr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class Widget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
    }
    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_today)
        try {
            val items = runBlocking { CalendarRepository(AppDb.get(ctx)).dayItems(todayStr()).first() }
            val lines = items.take(5)
            val views = listOf(R.id.w_l1, R.id.w_l2, R.id.w_l3, R.id.w_l4, R.id.w_l5)
            views.forEachIndexed { i, v ->
                val it = lines.getOrNull(i)
                rv.setTextViewText(v, if (it == null) "" else (if (it.allDay) "весь день" else it.start) + "  " + it.title)
            }
        } catch (e: Exception) { rv.setTextViewText(R.id.w_l1, "нет данных") }
        mgr.updateAppWidget(id, rv)
    }
}
