package com.family.daily.ui
import android.content.Intent

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
import com.family.daily.data.occursOn
import com.family.daily.data.dayOfWeekOf
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

    private fun pref() = requireContext().getSharedPreferences("app", 0)
    private fun firstDayDow(): Int = if (pref().getInt("firstDay", 1) == 1) 2 else 1
    private fun addDaysStr(n: Int): String { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, n); return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) }

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
        val share = Button(ctx).apply { text = "📤" }
        share.setOnClickListener { shareDay() }
        prev.setOnClickListener { month.add(Calendar.MONTH, -1); collectMonth() }
        next.setOnClickListener { month.add(Calendar.MONTH, 1); collectMonth() }
        todayBtn.setOnClickListener { month.timeInMillis = System.currentTimeMillis(); collectMonth() }
        hdr.addView(prev); hdr.addView(monthTitle); hdr.addView(next); hdr.addView(todayBtn); hdr.addView(share)
        root.addView(hdr)
        val modeRow = ctx.rowH()
        val gridBtn = Button(ctx).apply { text = "Месяц" }
        val listBtn = Button(ctx).apply { text = "Список (занятые дни)" }
        gridBtn.setOnClickListener { pref().edit().putString("calMode", "grid").apply(); collectMonth() }
        listBtn.setOnClickListener { pref().edit().putString("calMode", "list").apply(); collectMonth() }
        modeRow.addView(gridBtn); modeRow.addView(listBtn)
        root.addView(modeRow)
        grid = ctx.colV(); root.addView(grid)
        return scroll
    }

    override fun onResume() { super.onResume(); collectMonth() }

    private fun shareDay() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = CalendarRepository(db()).dayItems(todayStr()).first()
                val text = "План на " + todayStr() + ":\n" + (if (items.isEmpty()) "свободно" else items.joinToString("\n") { (if (it.allDay) "весь день" else it.start) + " — " + it.title })
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться днём"))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { showCrashDialog(ctx, e) }
        }
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        collectMonth()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                CalendarRepository(db()).dayItems(todayStr()).collect { items -> renderToday(items) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private fun renderToday(items: List<DayItem>) {
        val ctx = requireContext()
        todayBox.removeAllViews()
        todayBox.addView(ctx.tv("Сегодня", 16f, true))
        if (items.isEmpty()) todayBox.addView(ctx.tv("Сегодня событий нет", 13f))
        items.take(6).forEach { item ->
            val r = ctx.rowH()
            r.addView(ctx.bar(colorOf(item.categoryId)))
            r.addView(ctx.tv(if (item.allDay) "весь день" else item.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
            val t = ctx.tv(item.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(t)
            if (item.kind == "ev" || item.kind == "rep") { val eid = item.id; r.setOnClickListener { showEventView(ctx, eid) } }
            todayBox.addView(r)
        }
    }

    private fun collectMonth() {
        if (!isAdded) return
        monthJob?.cancel()
        val y = month.get(Calendar.YEAR); val m = month.get(Calendar.MONTH)
        val last = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val from = String.format("%04d-%02d-01", y, m + 1)
        val to = String.format("%04d-%02d-%02d", y, m + 1, last)
        monthTitle.text = month.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale("ru")).toString() + " " + y
        if (pref().getString("calMode", "grid") == "list") { renderList(); return }
        monthJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                combine(db().events().between(from, to), db().school().all(), db().templates().all(), db().events().repeating()) { evs, sch, tpl, reps ->
                    val map = HashMap<String, MutableList<Long>>(); val allday = HashSet<String>()
                    evs.forEach { e -> map.getOrPut(e.date) { mutableListOf() }.add(e.categoryId); if (e.allDay) allday.add(e.date) }
                    val cal = Calendar.getInstance(); cal.set(y, m, 1)
                    for (d in 1..last) {
                        val dow = cal.get(Calendar.DAY_OF_WEEK)
                        val ds = String.format("%04d-%02d-%02d", y, m + 1, d)
                        sch.filter { s2 -> s2.enabled && s2.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }.forEach { s2 -> map.getOrPut(ds) { mutableListOf() }.add(3L) }
                        tpl.filter { t2 -> t2.dayOfWeek == dow }.forEach { t2 -> map.getOrPut(ds) { mutableListOf() }.add(t2.categoryId) }
                        reps.filter { r2 -> occursOn(r2, ds) }.forEach { r2 -> map.getOrPut(ds) { mutableListOf() }.add(r2.categoryId) }
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    Pair(map, allday)
                }.collect { pair -> renderGrid(y, m, last, pair.first, pair.second) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private fun renderList() {
        val ctx = requireContext()
        grid.removeAllViews()
        monthJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = db()
                val from = todayStr(); val to = addDaysStr(30)
                val evs = db.events().between(from, to).first()
                val reps = db.events().repeating().first()
                val sch = db.school().all().first()
                val tpl = db.templates().all().first()
                grid.addView(ctx.tv("Ближайшие 30 дней — только занятые дни", 14f, true))
                var any = false
                for (i in 0..30) {
                    val ds = addDaysStr(i)
                    val items = mutableListOf<DayItem>()
                    evs.filter { it.date == ds }.forEach { items.add(DayItem("ev", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
                    reps.filter { occursOn(it, ds) }.forEach { items.add(DayItem("rep", it.id, it.title, it.start, it.end, it.allDay, it.categoryId, it.silent)) }
                    sch.filter { s2 -> s2.enabled && s2.days.split(",").mapNotNull { x -> x.toIntOrNull() }.contains(dow) }.forEach { s2 -> items.add(DayItem("school", s2.id, "В школе", s2.start, s2.end, false, 3, true)) }
                    tpl.filter { t2 -> t2.dayOfWeek == dow }.forEach { t2 -> items.add(DayItem("tpl", t2.id, t2.title, t2.start, t2.end, false, t2.categoryId, t2.silent)) }
                    tpl.filter { t2 -> t2.dayOfWeek == dow }.forEach { items.add(DayItem("tpl", t2.id, t2.title, t2.start, t2.end, false, t2.categoryId, t2.silent)) }
                    if (items.isEmpty()) continue
                    any = true
                    val card = ctx.card(); val col = ctx.colV()
                    col.addView(ctx.tv(ds, 14f, true))
                    items.sortedBy { if (it.allDay) "00:00" else it.start }.forEach { it ->
                        val r = ctx.rowH()
                        r.addView(ctx.bar(colorOf(it.categoryId)))
                        r.addView(ctx.tv(if (it.allDay) "весь день" else it.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
                        val t = ctx.tv(it.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        r.addView(t)
                        if (it.kind == "ev" || it.kind == "rep") { val eid = it.id; r.setOnClickListener { showEventView(ctx, eid) } }
                        col.addView(r)
                    }
                    card.addView(col); grid.addView(card)
                }
                if (!any) grid.addView(ctx.tv("Нет событий в ближайшие 30 дней", 13f))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }
    private fun it2id(s: com.family.daily.data.SchoolSchedule) = s.id

    private fun renderGrid(y: Int, m: Int, last: Int, map: Map<String, MutableList<Long>>, allday: Set<String>) {
        val ctx = requireContext()
        grid.removeAllViews()
        val fd = firstDayDow()
        val names = if (fd == 2) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") else listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        val hdr = ctx.rowH()
        names.forEach { d ->
            val t = ctx.tv(d, 11f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); t.gravity = Gravity.CENTER; hdr.addView(t)
        }
        grid.addView(hdr)
        val c = Calendar.getInstance(); c.set(y, m, 1)
        val off = (c.get(Calendar.DAY_OF_WEEK) - fd + 7) % 7
        var row = newRow(ctx)
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
            if ((off + d) % 7 == 0) { grid.addView(row); row = newRow(ctx) }
        }
        if (row.childCount > 0) grid.addView(row)
    }

    private fun newRow(ctx: android.content.Context): LinearLayout =
        ctx.rowH().apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(4) } }

    private fun dayDialog(ds: String) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = CalendarRepository(db()).dayItems(ds).first()
                val box = ctx.colV().apply { setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(8)) }
                box.addView(ctx.tv(ds, 16f, true))
                if (items.isEmpty()) box.addView(ctx.tv("Событий нет", 13f))
                items.forEach { item ->
                    val r = ctx.rowH()
                    r.addView(ctx.bar(colorOf(item.categoryId)))
                    r.addView(ctx.tv(if (item.allDay) "весь день" else item.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
                    val t = ctx.tv(item.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    r.addView(t)
                    if (item.kind == "ev" || item.kind == "rep") { val eid = item.id; r.setOnClickListener { showEventView(ctx, eid) } }
                    box.addView(r)
                }
                val add = Button(ctx).apply {
                    text = "+ событие"
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setOnClickListener { EventFormDialog(ctx, presetDate = ds).show() }
                }
                box.addView(add)
                box.addView(ctx.tv("Подсказка: долгое нажатие на день — быстрое создание", 11f))
                AlertDialog.Builder(ctx).setView(box).setNegativeButton("Закрыть", null).show()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { showCrashDialog(ctx, e) }
        }
    }
}
