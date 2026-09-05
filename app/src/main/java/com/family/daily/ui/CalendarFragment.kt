package com.family.daily.ui
import com.family.daily.data.RepeatException
import com.family.daily.data.repOccurs
import com.family.daily.data.schoolTimesFor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    private lateinit var familyCard: View
    private lateinit var familyBox: LinearLayout
    private lateinit var dayTitle: TextView
    private lateinit var dayBox: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var monthTitle: TextView
    private var monthJob: Job? = null
    private var dayJob: Job? = null
    private var selectedDay = todayStr()

    private fun pref() = requireContext().getSharedPreferences("app", 0)
    private fun simple() = pref().getBoolean("simpleMode", false)
    private fun firstDayDow(): Int = if (pref().getInt("firstDay", 1) == 1) 2 else 1
    private fun smallBtn(ctx: Context, text: String): Button = Button(ctx).apply { this.text = text; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0 }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        root = ctx.colV().apply { setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(140)) }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        familyCard = ctx.card(); familyBox = ctx.colV(); (familyCard as android.view.ViewGroup).addView(familyBox)
        familyCard.visibility = View.GONE
        root.addView(familyCard)
        val tc = ctx.card()
        val tcol = ctx.colV()
        dayTitle = ctx.tv("Сегодня", 16f, true)
        dayBox = ctx.colV()
        tcol.addView(dayTitle); tcol.addView(dayBox)
        tcol.addView(ctx.tv("Тап по дню — показать события здесь. Долгое нажатие — новое событие. Тап по событию — редактировать.", 11f))
        tc.addView(tcol); root.addView(tc)
        val hdr = ctx.rowH()
        val prev = smallBtn(ctx, "‹")
        monthTitle = ctx.tv("", 16f, true); monthTitle.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); monthTitle.gravity = Gravity.CENTER
        val next = smallBtn(ctx, "›")
        val todayBtn = smallBtn(ctx, "Сегодня")
        val share = smallBtn(ctx, "📤")
        share.setOnClickListener { shareDay() }
        prev.setOnClickListener { month.add(Calendar.MONTH, -1); collectMonth() }
        next.setOnClickListener { month.add(Calendar.MONTH, 1); collectMonth() }
        todayBtn.setOnClickListener { month.timeInMillis = System.currentTimeMillis(); selectedDay = todayStr(); collectMonth(); collectDay() }
        hdr.addView(prev); hdr.addView(monthTitle); hdr.addView(next); hdr.addView(todayBtn); hdr.addView(share)
        root.addView(hdr)
        val modeRow = ctx.rowH()
        val gridBtn = smallBtn(ctx, "Месяц")
        val listBtn = smallBtn(ctx, if (simple()) "Ближайшие 7 дней" else "Список (занятые дни)")
        gridBtn.setOnClickListener { pref().edit().putString("calMode", "grid").apply(); collectMonth() }
        listBtn.setOnClickListener { pref().edit().putString("calMode", "list").apply(); collectMonth() }
        modeRow.addView(gridBtn); modeRow.addView(listBtn)
        root.addView(modeRow)
        grid = ctx.colV(); root.addView(grid)
        return scroll
    }

    override fun onResume() { super.onResume(); collectMonth(); collectDay(); loadFamilyCard() }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        collectMonth()
        collectDay()
        loadFamilyCard()
    }

    private fun loadFamilyCard() {
        if (!isAdded) return
        val ctx = requireContext()
        if (!simple()) { familyCard.visibility = View.GONE; return }
        familyCard.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val members = db().members().all().first()
                familyBox.removeAllViews()
                familyBox.addView(ctx.tv("Связаться с семьёй", 18f, true))
                val withPhone = members.filter { it.phone.isNotBlank() }
                if (withPhone.isEmpty()) familyBox.addView(ctx.tv("Добавьте телефоны: Семья → Семья → ✏️", 13f))
                withPhone.forEach { m ->
                    val b = Button(ctx).apply {
                        text = "📞 " + m.name
                        textSize = 18f * ctx.fontScale()
                        minWidth = 0; minimumWidth = 0
                        setOnClickListener {
                            try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + m.phone.replace(Regex("[^0-9+]"), "")))) } catch (_: Exception) {}
                        }
                    }
                    familyBox.addView(b)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private fun shareDay() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = CalendarRepository(db()).dayItems(selectedDay).first()
                val text = "План на " + selectedDay + ":\n" + (if (items.isEmpty()) "свободно" else items.joinToString("\n") { (if (it.allDay) "весь день" else it.start) + " — " + it.title })
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться днём"))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { showCrashDialog(ctx, e) }
        }
    }

    private fun collectDay() {
        if (!isAdded) return
        dayJob?.cancel()
        dayJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                CalendarRepository(db()).dayItems(selectedDay).collect { items -> renderDayCard(items) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private fun renderDayCard(items: List<DayItem>) {
        val ctx = requireContext()
        val cancelled = (pref().getString("cancelledDays", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
        dayTitle.text = if (selectedDay == todayStr()) "Сегодня · " + dayOfWeekName(selectedDay) else selectedDay + " · " + dayOfWeekName(selectedDay)
        dayBox.removeAllViews()
        if (cancelled.contains(selectedDay)) {
            dayBox.addView(ctx.tv("✖ День отменён (болезнь/ЧП)", 14f, true, color = Color.parseColor("#E53935")))
            dayBox.addView(Button(ctx).apply { text = "Вернуть день"; minWidth = 0; minimumWidth = 0; setOnClickListener { restoreDay() } })
            return
        }
        if (items.isEmpty()) dayBox.addView(ctx.tv("Событий нет", 13f))
        items.forEach { item ->
            val r = ctx.rowH()
            r.addView(ctx.bar(colorOf(item.categoryId)))
            r.addView(ctx.tv(if (item.allDay) "весь день" else item.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
            val t = ctx.tv(item.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(t)
            if (item.kind == "ev" || item.kind == "rep") { val eid = item.id; r.setOnClickListener { showEventView(ctx, eid) }; r.setOnLongClickListener { copyEventDialog(ctx, eid); true } } else if (item.kind == "note") { val nid = item.id; r.setOnClickListener { showNoteView(ctx, nid) } }
            dayBox.addView(r)
        }
        val cancel = Button(ctx).apply {
            text = "✖ Отменить день (болезнь/ЧП)"; minWidth = 0; minimumWidth = 0
            setOnClickListener {
                android.app.AlertDialog.Builder(ctx).setMessage("Пометить все события " + selectedDay + " как «Отменено»?")
                    .setPositiveButton("Отменить день") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            db().events().onDay(selectedDay).first().forEach { e -> db().events().update(e.copy(status = "Отменён")) }
                            db().events().repeatingList().filter { r -> occursOn(r, selectedDay) }.forEach { r -> db().repeatExceptions().insert(RepeatException(eventId = r.id, date = selectedDay)) }
                            val p = pref()
                            val set = (p.getString("cancelledDays", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
                            set.add(selectedDay)
                            p.edit().putString("cancelledDays", set.joinToString(",")).apply()
                            toast("День отменён")
                            collectDay(); collectMonth()
                        }
                    }.setNegativeButton("Нет", null).show()
            }
        }
        if (!cancelled.contains(selectedDay)) dayBox.addView(cancel)
    }

    private fun copyEventDialog(ctx: android.content.Context, eventId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val e = db().events().byId(eventId) ?: return@launch
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(ctx, { _, y, mo, d ->
                val nd = String.format("%04d-%02d-%02d", y, mo + 1, d)
                viewLifecycleOwner.lifecycleScope.launch {
                    val newId = db().events().insert(e.copy(id = 0, date = nd))
                    db().participants().membersOf(eventId).forEach { mid -> db().participants().insert(com.family.daily.data.EventParticipant(newId, mid)) }
                    toast("Скопировано на " + nd)
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun restoreDay() {
        viewLifecycleOwner.lifecycleScope.launch {
            db().repeatExceptions().deleteDate(selectedDay)
            val p = pref()
            val set = (p.getString("cancelledDays", "") ?: "").split(",").filter { it.isNotBlank() && it != selectedDay }
            p.edit().putString("cancelledDays", set.joinToString(",")).apply()
            toast("День возвращён")
            collectDay(); collectMonth()
        }
    }

    private fun collectMonth() {
        if (!isAdded) return
        monthJob?.cancel()
        val y = month.get(Calendar.YEAR); val m = month.get(Calendar.MONTH)
        val last = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val from = String.format("%04d-%02d-01", y, m + 1)
        val to = String.format("%04d-%02d-%02d", y, m + 1, last)
        monthTitle.text = listOf("Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")[m] + " " + y
        if ((pref().getString("calMode", "grid") ?: "grid") == "list") { renderList(); return }
        monthJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cancelled = (pref().getString("cancelledDays", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
                combine(db().events().between(from, to), db().school().all(), db().templates().all(), db().events().repeating(), db().notes().all()) { evs, sch, tpl, reps, notes ->
                    val map = HashMap<String, MutableList<Long>>(); val allday = HashSet<String>()
                    evs.filter { e -> e.status != "Отменён" }.forEach { e -> map.getOrPut(e.date) { mutableListOf() }.add(e.categoryId); if (e.allDay) allday.add(e.date) }
                    val cal = Calendar.getInstance(); cal.set(y, m, 1)
                    for (d in 1..last) {
                        val dow = cal.get(Calendar.DAY_OF_WEEK)
                        val ds = String.format("%04d-%02d-%02d", y, m + 1, d)
                        sch.forEach { s2 -> if (schoolTimesFor(s2, dow) != null) map.getOrPut(ds) { mutableListOf() }.add(s2.categoryId) }
                        tpl.filter { t2 -> t2.dayOfWeek == dow }.forEach { t2 -> map.getOrPut(ds) { mutableListOf() }.add(t2.categoryId) }
                        reps.filter { r2 -> occursOn(r2, ds) }.forEach { r2 -> map.getOrPut(ds) { mutableListOf() }.add(r2.categoryId) }
                        notes.filter { n2 -> !n2.done && n2.date.isNotBlank() && n2.date <= ds && (n2.date == ds || repOccurs(n2.repeatType, n2.repeatDays, n2.date, ds)) }.forEach { map.getOrPut(ds) { mutableListOf() }.add(7L) }
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    cancelled.forEach { map.remove(it); allday.remove(it) }
                    Pair(map, allday)
                }.collect { pair -> renderGrid(y, m, last, pair.first, pair.second) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private fun renderList() {
        val ctx = requireContext()
        grid.removeAllViews()
        val days = if (simple()) 7 else 30
        monthJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = db()
                val from = todayStr(); val to = addDaysStr(days)
                val cancelled = (pref().getString("cancelledDays", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
                val evs = db.events().between(from, to).first()
                val reps = db.events().repeating().first()
                val sch = db.school().all().first()
                val tpl = db.templates().all().first()
                val excepts = db.repeatExceptions().allList()
                grid.addView(ctx.tv((if (simple()) "Ближайшие 7 дней" else "Ближайшие 30 дней — только занятые дни"), 14f, true))
                var any = false
                for (i in 0..days) {
                    val ds = addDaysStr(i)
                    if (cancelled.contains(ds)) continue
                    val dow = dayOfWeekOf(ds)
                    val items = mutableListOf<DayItem>()
                    evs.filter { e -> e.date == ds && e.status != "Отменён" }.forEach { e -> items.add(DayItem("ev", e.id, e.title, e.start, e.end, e.allDay, e.categoryId, e.silent)) }
                    reps.filter { r -> occursOn(r, ds) && excepts.none { ex -> ex.eventId == r.id && ex.date == ds } }.forEach { r -> items.add(DayItem("rep", r.id, r.title, r.start, r.end, r.allDay, r.categoryId, r.silent)) }
                    sch.forEach { s -> schoolTimesFor(s, dow)?.let { t -> items.add(DayItem("school", s.id, s.title, t.first, t.second, false, s.categoryId, true, s.childId)) } }
                    tpl.filter { t -> t.dayOfWeek == dow }.forEach { t -> items.add(DayItem("tpl", t.id, t.title, t.start, t.end, false, t.categoryId, t.silent)) }
                    if (items.isEmpty()) continue
                    any = true
                    val card = ctx.card(); val col = ctx.colV()
                    col.addView(ctx.tv(ds, 14f, true))
                    items.sortedBy { if (it.allDay) "00:00" else it.start }.forEach { item ->
                        val r = ctx.rowH()
                        r.addView(ctx.bar(colorOf(item.categoryId)))
                        r.addView(ctx.tv(if (item.allDay) "весь день" else item.start, 12f).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(70), ViewGroup.LayoutParams.WRAP_CONTENT) })
                        val t = ctx.tv(item.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        r.addView(t)
                        if (item.kind == "ev" || item.kind == "rep") { val eid = item.id; r.setOnClickListener { showEventView(ctx, eid) }; r.setOnLongClickListener { copyEventDialog(ctx, eid); true } } else if (item.kind == "note") { val nid = item.id; r.setOnClickListener { showNoteView(ctx, nid) } }
                        col.addView(r)
                    }
                    card.addView(col); grid.addView(card)
                }
                if (!any) grid.addView(ctx.tv("Нет событий", 13f))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private fun renderGrid(y: Int, m: Int, last: Int, map: Map<String, MutableList<Long>>, allday: Set<String>) {
        val ctx = requireContext()
        val cancelled = (pref().getString("cancelledDays", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
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
            if (ds == selectedDay) bg.setStroke(ctx.dp(2), Color.parseColor("#1E88E5"))
            if (cancelled.contains(ds)) bg.setColor(Color.parseColor("#DDDDDD"))
            cell.background = bg
            cell.isClickable = true
            cell.isFocusable = true
            cell.layoutParams = LinearLayout.LayoutParams(0, ctx.dp(44), 1f)
            cell.addView(ctx.tv(d.toString(), 13f, ds == todayStr()).apply { if (cancelled.contains(ds)) setTextColor(Color.parseColor("#616161")) })
            val dots = ctx.rowH()
            (map[ds] ?: emptyList()).distinct().take(3).forEach { cat ->
                val v = View(ctx); v.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), ctx.dp(6)).apply { marginEnd = ctx.dp(2) }
                v.setBackgroundColor(colorOf(cat)); dots.addView(v)
            }
            cell.addView(dots)
            if (allday.contains(ds)) { val b = View(ctx); b.layoutParams = LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(4)); b.setBackgroundColor(colorOf(map[ds]?.first() ?: 1L)); cell.addView(b) }
            cell.setOnClickListener { selectedDay = ds; collectDay(); collectMonth() }
            cell.setOnLongClickListener { toast("Создать событие на " + ds); EventFormDialog(ctx, presetDate = ds).show(); true }
            row.addView(cell)
            if ((off + d) % 7 == 0) { grid.addView(row); row = newRow(ctx) }
        }
        while (row.childCount < 7) row.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, ctx.dp(44), 1f) }); if (row.childCount > 0) grid.addView(row)
    }

    private fun newRow(ctx: Context): LinearLayout =
        ctx.rowH().apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(4) } }
}
