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
    private lateinit var root: LinearLayout
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        root = ctx.colV().apply { setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(96)) }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }
    override fun onViewCreated(v: View, s: Bundle?) { super.onViewCreated(v, s); render() }
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
            chips.addView(Button(ctx).apply { text = name; setOnClickListener { panel = idx; render() } })
        }
        root.addView(chips)
        when (panel) { 0 -> bookings(ctx, db); 1 -> clients(ctx, db); 2 -> services(ctx, db) }
    }
    private suspend fun bookings(ctx: android.content.Context, db: AppDb) {
        val list = db.events().byCat(1).first()
        if (list.isEmpty()) root.addView(ctx.tv("Записей нет. Нажмите + → «Запись клиента».", 14f))
        list.forEach { e ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(e.title, 15f, true))
            col.addView(ctx.tv(e.date + " · " + e.start + "–" + e.end, 13f))
            col.addView(ctx.tv("Статус: " + e.status, 13f, color = colorOf(1)))
            val btns = ctx.rowH()
            btns.addView(Button(ctx).apply {
                text = "Статус →"
                setOnClickListener {
                    val next = STATUSES[(STATUSES.indexOf(e.status) + 1) % STATUSES.size]
                    viewLifecycleOwner.lifecycleScope.launch { db.events().update(e.copy(status = next)); render() }
                }
            })
            btns.addView(Button(ctx).apply { text = "Изменить"; setOnClickListener { EventFormDialog(ctx, e) { render() }.show() } })
            col.addView(btns); card.addView(col); root.addView(card)
        }
    }
    private suspend fun clients(ctx: android.content.Context, db: AppDb) {
        val list = db.clients().all().first()
        if (list.isEmpty()) root.addView(ctx.tv("Клиентов нет. Добавьте первого.", 14f))
        list.forEach { c ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(c.name, 15f, true))
            col.addView(ctx.tv(c.phone + " · " + c.source, 13f))
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
                        h.forEach { e -> box.addView(ctx.tv(e.date + " — " + e.title + " — " + (e.price?.toInt() ?: 0) + " ₽ (" + e.status + ")", 13f)) }
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
