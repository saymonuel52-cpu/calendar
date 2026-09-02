package com.family.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
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
            try {
                val ctx = requireContext(); val db = db()
                root.removeAllViews()
                root.addView(ctx.tv("Школа, секции, садик", 16f, true))
                root.addView(ctx.tv("У каждого дня недели — своё время. Планируйте записи вокруг них.", 12f))
                val list = db.school().all().first()
                val members = db.members().all().first()
                if (list.isEmpty()) root.addView(ctx.tv("Расписаний нет. Добавьте школу, самбо, садик…", 14f))
                list.forEach { s ->
                    val child = members.find { it.id == s.childId }
                    val card = ctx.card(); val col = ctx.colV()
                    col.addView(ctx.tv((child?.name ?: "Ребёнок") + " — " + s.title, 15f, true))
                    if (s.dayTimes.isNotBlank()) {
                        s.dayTimes.split(",").forEach { part ->
                            val eq = part.split("=")
                            if (eq.size == 2) {
                                val dv = eq[0].toIntOrNull(); val idx = DAY_VALS.indexOf(dv)
                                if (idx >= 0) col.addView(ctx.tv(DAY_LABELS[idx] + " · " + eq[1].replace("-", "–"), 13f, color = colorOf(s.categoryId)))
                            }
                        }
                    } else {
                        col.addView(ctx.tv(daysLabel(s.days) + " · " + s.start + "–" + s.end, 13f, color = colorOf(s.categoryId)))
                    }
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
            f.label("Недельная сетка (у каждого дня своё время)")
            val initMap = parseInit(existing)
            val dayChecks = mutableListOf<CheckBox>(); val dayStarts = mutableListOf<TimeBtn>(); val dayEnds = mutableListOf<TimeBtn>()
            DAY_VALS.forEachIndexed { i, dv ->
                val row = ctx.rowH()
                val cb = CheckBox(ctx).apply { text = DAY_LABELS[i]; isChecked = initMap.containsKey(dv) }
                val st = TimeBtn(ctx, initMap[dv]?.first ?: "08:00")
                val en = TimeBtn(ctx, initMap[dv]?.second ?: "12:00")
                st.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                en.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(cb); row.addView(st); row.addView(en)
                f.add(row)
                dayChecks.add(cb); dayStarts.add(st); dayEnds.add(en)
            }
            val cat = f.spin(CAT_NAMES); cat.setSelection(((existing?.categoryId ?: 3L) - 1).toInt())
            val en = f.check("ходит", existing?.enabled ?: true)
            val sv = ScrollView(ctx).apply { addView(f.root) }
            android.app.AlertDialog.Builder(ctx).setTitle(if (existing == null) "Новое расписание" else "Расписание").setView(sv)
                .setPositiveButton("Сохранить") { _, _ ->
                    val t = title.text.toString().trim()
                    if (t.isEmpty()) { Toast.makeText(ctx, "Введите название", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    val sb = StringBuilder(); val daysList = mutableListOf<Int>(); var firstS = ""; var firstE = ""
                    DAY_VALS.forEachIndexed { i, dv ->
                        if (dayChecks[i].isChecked) {
                            if (daysList.isNotEmpty()) sb.append(",")
                            sb.append(dv).append("=").append(dayStarts[i].value).append("-").append(dayEnds[i].value)
                            if (firstS.isEmpty()) { firstS = dayStarts[i].value; firstE = dayEnds[i].value }
                            daysList.add(dv)
                        }
                    }
                    if (daysList.isEmpty()) { Toast.makeText(ctx, "Отметьте хотя бы один день", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    scope.launch {
                        val data = SchoolSchedule(id = existing?.id ?: 0, childId = kids[child.selectedItemPosition].id, start = firstS, end = firstE, enabled = en.isChecked, days = daysList.joinToString(","), title = t, categoryId = (cat.selectedItemPosition + 1).toLong(), dayTimes = sb.toString())
                        if (existing != null) db.school().update(data) else db.school().insert(data)
                        onSaved?.invoke()
                    }
                }.setNegativeButton("Отмена", null).show()
        }
    }
    private fun parseInit(e: SchoolSchedule?): Map<Int, Pair<String, String>> {
        val map = mutableMapOf<Int, Pair<String, String>>()
        if (e == null) return map
        if (e.dayTimes.isNotBlank()) {
            e.dayTimes.split(",").forEach { part ->
                val eq = part.split("=")
                if (eq.size == 2) { val d = eq[0].toIntOrNull(); val se = eq[1].split("-"); if (d != null && se.size == 2) map[d] = se[0] to se[1] }
            }
        } else {
            e.days.split(",").mapNotNull { it.toIntOrNull() }.forEach { d -> map[d] = e.start to e.end }
        }
        return map
    }
}
