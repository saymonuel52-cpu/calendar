package com.family.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.AppDb
import com.family.daily.data.SchoolSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SchoolFragment : Fragment() {
    private lateinit var root: android.widget.LinearLayout
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
            try {
                val ctx = requireContext(); val db = db()
                root.removeAllViews()
                root.addView(ctx.tv("Школа: время «ребёнок в школе»", 16f, true))
                root.addView(ctx.tv("Эти интервалы автоматически показываются в календаре зелёным — планируйте записи вокруг них.", 12f))
                val list = db.school().all().first()
                val members = db.members().all().first()
                if (list.isEmpty()) root.addView(ctx.tv("Расписания нет. Добавьте ребёнка и время.", 14f))
                list.forEach { s ->
                    val child = members.find { it.id == s.childId }
                    val card = ctx.card(); val col = ctx.colV()
                    col.addView(ctx.tv((child?.name ?: "Ребёнок") + " · Пн–Пт · " + s.start + "–" + s.end, 15f, true))
                    if (!s.enabled) col.addView(ctx.tv("не ходит (каникулы/выходной)", 12f))
                    col.addView(Button(ctx).apply { text = "✏️ Изменить"; setOnClickListener { SchoolDialog(ctx, s) { render() }.show() } })
                    card.addView(col); root.addView(card)
                }
                root.addView(Button(ctx).apply { text = "+ Расписание ребёнка"; setOnClickListener { SchoolDialog(ctx) { render() }.show() } })
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }
}

class SchoolDialog(private val ctx: android.content.Context, private val existing: SchoolSchedule? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val kids = db.members().all().first().filter { it.role == "child" }
            if (kids.isEmpty()) { Toast.makeText(ctx, "Сначала добавьте ребёнка в Семья → Семья", Toast.LENGTH_LONG).show(); return@launch }
            val f = Form(ctx)
            val child = f.spin(kids.map { it.name })
            existing?.let { kids.indexOfFirst { k -> k.id == it.childId }.takeIf { i -> i >= 0 }?.let { child.setSelection(it) } }
            f.label("Начало"); val start = TimeBtn(ctx, existing?.start ?: "08:00"); f.add(start)
            f.label("Конец"); val end = TimeBtn(ctx, existing?.end ?: "13:00"); f.add(end)
            val en = f.check("ходит в школу", existing?.enabled ?: true)
            android.app.AlertDialog.Builder(ctx).setTitle("Расписание школы").setView(f.root)
                .setPositiveButton("Сохранить") { _, _ ->
                    scope.launch {
                        val data = SchoolSchedule(id = existing?.id ?: 0, childId = kids[child.selectedItemPosition].id, start = start.value, end = end.value, enabled = en.isChecked)
                        if (existing != null) db.school().update(data) else db.school().insert(data)
                        onSaved?.invoke()
                    }
                }.setNegativeButton("Отмена", null).show()
        }
    }
}
