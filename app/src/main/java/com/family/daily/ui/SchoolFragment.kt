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
                root.addView(ctx.tv("Школа, секции, садик", 16f, true))
                root.addView(ctx.tv("Эти интервалы показываются в календаре цветом категории — планируйте записи вокруг них.", 12f))
                val list = db.school().all().first()
                val members = db.members().all().first()
                if (list.isEmpty()) root.addView(ctx.tv("Расписаний нет. Добавьте школу, самбо, садик…", 14f))
                list.forEach { s ->
                    val child = members.find { it.id == s.childId }
                    val card = ctx.card(); val col = ctx.colV()
                    col.addView(ctx.tv((child?.name ?: "Ребёнок") + " — " + s.title, 15f, true))
                    col.addView(ctx.tv(daysLabel(s.days) + " · " + s.start + "–" + s.end, 13f, color = colorOf(s.categoryId)))
                    if (!s.enabled) col.addView(ctx.tv("не ходит (каникулы/выходной)", 12f))
                    col.addView(Button(ctx).apply { text = "✏️ Изменить"; minWidth = 0; minimumWidth = 0; setOnClickListener { SchoolDialog(ctx, s) { render() }.show() } })
                    card.addView(col); root.addView(card)
                }
                root.addView(Button(ctx).apply { text = "+ Расписание"; setOnClickListener { SchoolDialog(ctx) { render() }.show() } })
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }
    private fun daysLabel(days: String): String {
        val set = days.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        return DAY_VALS.mapIndexedNotNull { i, v -> if (set.contains(v)) DAY_LABELS[i] else null }.joinToString(", ")
    }
}

class SchoolDialog(private val ctx: android.content.Context, private val existing: SchoolSchedule? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val kids = db.members().all().first().filter { it.role == "child" }
            if (kids.isEmpty()) {
                android.app.AlertDialog.Builder(ctx).setMessage("Нужен член семьи с ролью «ребёнок». Добавить сейчас?")
                    .setPositiveButton("Добавить ребёнка") { _, _ -> MemberDialog(ctx, presetRole = "child") { SchoolDialog(ctx, existing, onSaved).show() }.show() }
                    .setNegativeButton("Отмена", null).show()
                return@launch
            }
            val f = Form(ctx)
            val title = f.edit("Название *", existing?.title ?: "Школа")
            val child = f.spin(kids.map { it.name })
            existing?.let { kids.indexOfFirst { k -> k.id == it.childId }.takeIf { i -> i >= 0 }?.let { child.setSelection(it) } }
            val dayVals = mutableListOf<Int>()
            dayVals.addAll((existing?.days ?: "2,3,4,5,6").split(",").mapNotNull { it.toIntOrNull() })
            fun daysText() = "Дни: " + (if (dayVals.isEmpty()) "не выбраны" else DAY_VALS.mapIndexedNotNull { i, v -> if (dayVals.contains(v)) DAY_LABELS[i] else null }.joinToString(", "))
            val daysBtn = Button(ctx).apply { text = daysText() }; f.add(daysBtn)
            daysBtn.setOnClickListener {
                val checked = DAY_VALS.map { dayVals.contains(it) }.toBooleanArray()
                android.app.AlertDialog.Builder(ctx).setTitle("Какие дни?").setMultiChoiceItems(DAY_LABELS.toTypedArray(), checked) { _, which, isC ->
                    val dv = DAY_VALS[which]; if (isC) { if (!dayVals.contains(dv)) dayVals.add(dv) } else dayVals.remove(dv)
                }.setPositiveButton("ОК") { _, _ -> daysBtn.text = daysText() }.show()
            }
            f.label("Начало"); val start = TimeBtn(ctx, existing?.start ?: "08:00"); f.add(start)
            f.label("Конец"); val end = TimeBtn(ctx, existing?.end ?: "13:00"); f.add(end)
            val cat = f.spin(CAT_NAMES); cat.setSelection(((existing?.categoryId ?: 3L) - 1).toInt())
            val en = f.check("ходит", existing?.enabled ?: true)
            android.app.AlertDialog.Builder(ctx).setTitle(if (existing == null) "Новое расписание" else "Расписание").setView(f.root)
                .setPositiveButton("Сохранить") { _, _ ->
                    val t = title.text.toString().trim()
                    if (t.isEmpty()) { Toast.makeText(ctx, "Введите название", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    if (dayVals.isEmpty()) { Toast.makeText(ctx, "Выберите дни", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    scope.launch {
                        val data = SchoolSchedule(id = existing?.id ?: 0, childId = kids[child.selectedItemPosition].id, start = start.value, end = end.value, enabled = en.isChecked, days = dayVals.joinToString(","), title = t, categoryId = (cat.selectedItemPosition + 1).toLong())
                        if (existing != null) db.school().update(data) else db.school().insert(data)
                        onSaved?.invoke()
                    }
                }.setNegativeButton("Отмена", null).show()
        }
    }
}
