package com.family.daily.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.CalendarRepository
import com.family.daily.data.DayItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarFragment : Fragment() {
    private val month = Calendar.getInstance()
    private lateinit var root: LinearLayout
    private lateinit var todayBox: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var monthTitle: TextView
    private var monthJob: Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        root = ctx.colV().apply { setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(96)) }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tc = ctx.card(); todayBox = ctx.colV(); tc.addView(todayBox); root.addView(tc)
        val hdr = ctx.rowH()
        val prev = Button(ctx).apply { text = "‹" }
        monthTitle = ctx.tv("", 16f, true); monthTitle.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); monthTitle.gravity = Gravity.CENTER
        val next = Button(ctx).apply { text = "›" }
        val todayBtn = Button(ctx).apply { text = "Сегодня" }
        prev.setOnClickListener { month.add(Calendar.MONTH, -1); collectMonth() }
        next.setOnClickListener { month.add(Calendar.MONTH, 1); collectMonth() }
        todayBtn.setOnClickListener { month.timeInMillis = System.currentTimeMillis(); collectMonth() }
        hdr.addView(prev); hdr.addView(monthTitle); hdr.addView(next); hdr.addView(todayBtn)
        root.addView(hdr)
        grid = ctx.colV(); root.addView(grid)
        return scroll
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        collectMonth()
        viewLifecycleOwner.lifecycleScope.launch {
            CalendarRepository(db()).dayItems(todayStr()).collect { renderToday(it) }
        }
    }

    private fun renderToday(items: List<DayItem>) {
        val ctx = requireContext()
        todayBox.removeAllViews()
        todayBox.addView(ctx.tv("Сегодня", 16f, true))
        if (items.isEmpty()) todayBox.addView(ctx.tv("Сегодня событий нет", 13f))
        items.take(6).forEach { it ->
            val r = ctx.rowH()
            r.addView(ctx.bar(colorOf(it.categoryId)))
            r.addView(ctx.tv(if (it.allDay) "весь день" else it.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
            val t = ctx.tv(it.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(t)
            if (it.kind == "ev") r.setOnClickListener { showEventView(ctx, it.id) }
            todayBox.addView(r)
        }
    }

    private fun collectMonth() {
        monthJob?.cancel()
        val y = month.get(Calendar.YEAR); val m = month.get(Calendar.MONTH)
        val last = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val from = String.format("%04d-%02d-01", y, m + 1)
        val to = String.format("%04d-%02d-%02d", y, m + 1, last)
        monthTitle.text = month.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale("ru")).toString() + " " + y
        monthJob = viewLifecycleOwner.lifecycleScope.launch {
            combine(db().events().between(from, to), db().school().all(), db().templates().all()) { evs, sch, tpl ->
                val map = HashMap<String, MutableList<Long>>(); val allday = HashSet<String>()
                evs.forEach { e -> map.getOrPut(e.date) { mutableListOf() }.add(e.categoryId); if (e.allDay) allday.add(e.date) }
                val cal = Calendar.getInstance(); cal.set(y, m, 1)
                for (d in 1..last) {
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val ds = String.format("%04d-%02d-%02d", y, m + 1, d)
                    sch.filter { it.enabled && it.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }.forEach { map.getOrPut(ds) { mutableListOf() }.add(3L) }
                    tpl.filter { it.dayOfWeek == dow }.forEach { map.getOrPut(ds) { mutableListOf() }.add(it.categoryId) }
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                Pair(map, allday)
            }.collect { (map, allday) -> renderGrid(y, m, last, map, allday) }
        }
    }

    private fun renderGrid(y: Int, m: Int, last: Int, map: Map<String, MutableList<Long>>, allday: Set<String>) {
        val ctx = requireContext()
        grid.removeAllViews()
        val hdr = ctx.rowH()
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { d ->
            val t = ctx.tv(d, 11f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); t.gravity = Gravity.CENTER; hdr.addView(t)
        }
        grid.addView(hdr)
        val c = Calendar.getInstance(); c.set(y, m, 1)
        val off = (c.get(Calendar.DAY_OF_WEEK) + 6) % 7
        var row = ctx.rowH().apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(4) } }
        for (i in 0 until off) row.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, ctx.dp(44), 1f) })
        for (d in 1..last) {
            val ds = String.format("%04d-%02d-%02d", y, m + 1, d)
            val cell = ctx.colV(); cell.gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable(); bg.cornerRadius = ctx.dp(8).toFloat()
            if (ds == todayStr()) bg.setStroke(ctx.dp(2), Color.parseColor("#1E88E5"))
            cell.background = bg
            cell.layoutParams = LinearLayout.LayoutParams(0, ctx.dp(44), 1f)
            cell.addView(ctx.tv(d.toString(), 13f, ds == todayStr()))
            val dots = ctx.rowH()
            (map[ds] ?: emptyList()).distinct().take(3).forEach { cat ->
                val v = View(ctx); v.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), ctx.dp(6)).apply { marginEnd = ctx.dp(2) }
                v.setBackgroundColor(colorOf(cat)); dots.addView(v)
            }
            cell.addView(dots)
            if (allday.contains(ds)) { val b = View(ctx); b.layoutParams = LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(4)); b.setBackgroundColor(colorOf(map[ds]?.first() ?: 1L)); cell.addView(b) }
            cell.setOnClickListener { dayDialog(ds) }
            cell.setOnLongClickListener { EventFormDialog(ctx, presetDate = ds).show(); true }
            row.addView(cell)
            if ((off + d) % 7 == 0) { grid.addView(row); row = ctx.rowH().apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(4) } } }
        }
        if (row.childCount > 0) grid.addView(row)
    }

    private fun dayDialog(ds: String) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val items = CalendarRepository(db()).dayItems(ds).first()
            val box = ctx.colV().apply { setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(8)) }
            box.addView(ctx.tv(ds, 16f, true))
            if (items.isEmpty()) box.addView(ctx.tv("Событий нет", 13f))
            items.forEach { it ->
                val r = ctx.rowH()
                r.addView(ctx.bar(colorOf(it.categoryId)))
                r.addView(ctx.tv(if (it.allDay) "весь день" else it.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
                val t = ctx.tv(it.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                r.addView(t)
                if (it.kind == "ev") r.setOnClickListener { showEventView(ctx, it.id) }
                box.addView(r)
            }
            box.addView(Button(ctx).apply { text = "+ Событие"; setOnClickListener { EventFormDialog(ctx, presetDate = ds).show() } })
            AlertDialog.Builder(ctx).setView(box).setNegativeButton("Закрыть", null).show()
        }
    }
}
