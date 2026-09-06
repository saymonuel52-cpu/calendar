package com.family.daily.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.AppDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WorkFragment : Fragment() {
    private var panel = 0
    private var filter = "month"
    private lateinit var root: LinearLayout
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        root = ctx.colV().apply { setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(96)) }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }
    override fun onViewCreated(v: View, s: Bundle?) { super.onViewCreated(v, s); render() }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            try { renderInner() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }
    private suspend fun renderInner() {
        val ctx = requireContext(); val db = db()
        root.removeAllViews()
        val done = db.events().monthDone(todayStr().substring(0, 7) + "%")
        val sum = done.sumOf { it.price ?: 0.0 }.toInt()
        root.addView(ctx.card().apply { addView(ctx.tv("Итог месяца: " + done.size + " " + pluralVizit(done.size) + " · " + sum + " ₽", 15f, true)) })
        val chips = ctx.rowH()
        listOf("Записи", "Клиенты", "Услуги").forEachIndexed { idx, name ->
            chips.addView(Button(ctx).apply { text = name; alpha = if (panel == idx) 1f else 0.6f; setOnClickListener { panel = idx; render() } })
        }
        root.addView(chips)
        if (panel == 0) {
            val frow = ctx.rowH()
            listOf("7 дней" to "week", "Месяц" to "month", "Все" to "all").forEach { (label, key) ->
                frow.addView(Button(ctx).apply {
                    text = label; minWidth = 0; minimumWidth = 0
                    alpha = if (filter == key) 1f else 0.5f
                    setOnClickListener { filter = key; render() }
                })
            }
            root.addView(frow)
        }
        when (panel) { 0 -> bookings(ctx, db); 1 -> clients(ctx, db); 2 -> services(ctx, db) }
    }
    private suspend fun bookings(ctx: android.content.Context, db: AppDb) {
        val all = db.events().byCat(1).first()
        val today = todayStr()
        val list = when (filter) {
            "week" -> all.filter { it.date >= today && it.date <= addDaysStr(7) }.sortedBy { it.date + it.start }
            "month" -> all.filter { it.date.substring(0, 7) == today.substring(0, 7) }.sortedBy { it.date + it.start }
            else -> all.sortedBy { it.date + it.start }
        }
        root.addView(Button(ctx).apply {
            text = "🌙 Закрытие дня: отметить итоги"
            minWidth = 0; minimumWidth = 0
            setOnClickListener { closeDayDialog(ctx, db) }
        })
        if (list.isEmpty()) root.addView(ctx.tv("Записей нет. Нажмите + → «Запись клиента».", 14f))
        list.forEach { e ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(e.title, 15f, true))
            col.addView(ctx.tv(e.date + " · " + e.start + "–" + e.end, 13f))
            col.addView(ctx.tv("Статус: " + e.status, 13f, color = colorOf(1)))
            val btns = ctx.rowH()
            val statSpin = android.widget.Spinner(ctx).apply {
                adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, STATUSES)
                setSelection(STATUSES.indexOf(e.status).coerceAtLeast(0))
                layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            statSpin.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    val ns = STATUSES[pos]
                    if (ns != e.status) viewLifecycleOwner.lifecycleScope.launch { db.events().update(e.copy(status = ns)); render() }
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
            btns.addView(statSpin)
            btns.addView(Button(ctx).apply { text = "✏️"; setOnClickListener { EventFormDialog(ctx, e) { render() }.show() } })
            col.addView(btns); card.addView(col); root.addView(card)
        }
    }
    private fun closeDayDialog(ctx: android.content.Context, db: AppDb) {
        viewLifecycleOwner.lifecycleScope.launch {
            val today = todayStr()
            val evs = db.events().workOn(today)
            if (evs.isEmpty()) { toast("Сегодня записей нет"); return@launch }
            val box = ctx.colV().apply { setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(8)) }
            box.addView(ctx.tv("Сегодня записей: " + evs.size, 16f, true))
            evs.forEach { e ->
                val r = ctx.rowH()
                val t = ctx.tv(e.start + " " + e.title, 14f); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                r.addView(t)
                r.addView(Button(ctx).apply { text = "✓"; minWidth = 0; minimumWidth = 0; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db.events().update(e.copy(status = "Завершено")); ctx.refreshWidget() } } })
                r.addView(Button(ctx).apply { text = "✗"; minWidth = 0; minimumWidth = 0; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db.events().update(e.copy(status = "Отменён")); ctx.refreshWidget() } } })
                box.addView(r)
            }
            val sum = evs.filter { it.status == "Завершено" }.sumOf { it.price ?: 0.0 }.toInt()
            box.addView(ctx.tv("Итог завершённых сегодня: " + sum + " ₽", 14f, true))
            val dlg = android.app.AlertDialog.Builder(ctx).setView(box).setNegativeButton("Закрыть", null).show()
            dlg.setOnDismissListener { render() }
        }
    }

    private suspend fun clients(ctx: android.content.Context, db: AppDb) {
        val list = db.clients().all().first()
        if (list.isEmpty()) root.addView(ctx.tv("Клиентов нет. Добавьте первого.", 14f))
        list.forEach { c ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(c.name, 15f, true))
            col.addView(ctx.tv((if (c.phone.isNotBlank()) c.phone else "") + (if (c.source.isNotBlank() && c.phone.isNotBlank()) " · " else "") + c.source, 13f))
            if (c.note.isNotBlank()) col.addView(ctx.tv(c.note, 12f))
            val btns = ctx.rowH()
            if (c.phone.isNotBlank()) btns.addView(Button(ctx).apply {
                text = "📞"
                setOnClickListener { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.replace(Regex("[^0-9+]"), "")))) } catch (_: Exception) {} }
            })
            btns.addView(Button(ctx).apply {
                text = "История"
                setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val h = db.events().byClient(c.id)
                        val box = ctx.colV().apply { setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(8)) }
                        if (h.isEmpty()) box.addView(ctx.tv("Визитов нет", 13f))
                        h.forEach { e -> box.addView(ctx.tv(e.date + " — " + e.title + " — " + (e.price?.toInt() ?: 0) + " ₽ [" + e.status + "]", 13f)) }
                        AlertDialog.Builder(ctx).setView(box).setNegativeButton("Закрыть", null).show()
                    }
                }
            })
            btns.addView(Button(ctx).apply { text = "Записать"; setOnClickListener { BookingDialog(ctx, c.id) { render() }.show() } })
            btns.addView(Button(ctx).apply { text = "✏️"; setOnClickListener { ClientDialog(ctx, c) { render() }.show() } })
            col.addView(btns); card.addView(col); root.addView(card)
        }
        root.addView(Button(ctx).apply { text = "+ Клиент"; setOnClickListener { ClientDialog(ctx) { render() }.show() } })
    }
    private suspend fun services(ctx: android.content.Context, db: AppDb) {
        val list = db.services().all().first()
        if (list.isEmpty()) root.addView(ctx.tv("Прайс пуст. Добавьте услуги.", 14f))
        list.forEach { s ->
            val card = ctx.card(); val row = ctx.rowH()
            val t = ctx.tv(s.name, 15f, true); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(t)
            row.addView(ctx.tv(s.duration.toString() + " мин · " + s.price.toInt() + " ₽", 13f))
            row.addView(Button(ctx).apply { text = "✏️"; setOnClickListener { ServiceDialog(ctx, s) { render() }.show() } })
            card.addView(row); root.addView(card)
        }
        root.addView(Button(ctx).apply { text = "+ Услуга"; setOnClickListener { ServiceDialog(ctx) { render() }.show() } })
    }
}
