package com.family.daily.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import com.family.daily.ReminderScheduler
import com.family.daily.data.AppDb
import com.family.daily.data.Event
import com.family.daily.data.EventParticipant
import com.family.daily.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

val CAT_NAMES = listOf("Работа", "Семья", "Школа", "Личное", "Здоровье", "Питомец")
val REM_OPTIONS = listOf("Без напоминания", "за 5 мин", "за 10 мин", "за 15 мин", "за 30 мин", "за 1 час", "за 2 часа")
val REM_VALUES = listOf(5, 10, 15, 30, 60, 120)
val REP_OPTIONS = listOf("Не повторять", "Ежедневно", "Еженедельно", "Ежемесячно", "Свои дни недели")
val DAY_LABELS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
val DAY_VALS = listOf(2, 3, 4, 5, 6, 7, 1)

class Form(private val ctx: Context) {
    val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(4)) }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(8) }
    fun label(t: String) { root.addView(ctx.tv(t, 13f, true), lp) }
    fun add(v: View) { root.addView(v, lp) }
    fun edit(hint: String, init: String = ""): EditText = EditText(ctx).apply { setText(init); setHint(hint); root.addView(this, lp) }
    fun check(t: String, init: Boolean = false): CheckBox = CheckBox(ctx).apply { text = t; isChecked = init; root.addView(this, lp) }
    fun spin(items: List<String>): Spinner = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items); root.addView(this, lp) }
}

class DateBtn(ctx: Context, init: String) : Button(ctx) {
    var value = init
    init { text = pretty(value); setOnClickListener { pick() } }
    private fun pretty(v: String): String = try { val p = v.split("-"); if (v.isBlank()) "выбрать дату" else p[2] + "." + p[1] + "." + p[0] } catch (e: Exception) { v }
    private fun pick() {
        val p = value.split("-"); val c = Calendar.getInstance()
        if (p.size == 3 && p[0].isNotBlank()) c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        DatePickerDialog(context, { _, y, mo, d -> value = String.format("%04d-%02d-%02d", y, mo + 1, d); text = pretty(value) },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
}

class TimeBtn(ctx: Context, init: String) : Button(ctx) {
    var value = init
    init { text = value; setOnClickListener { pick() } }
    private fun pick() {
        val p = value.split(":"); val h = p.getOrNull(0)?.toIntOrNull() ?: 8; val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(context, { _, hh, mm -> value = String.format("%02d:%02d", hh, mm); text = value }, h, m, true).show()
    }
}

class EventFormDialog(private val ctx: Context, private val existing: Event? = null, private val presetDate: String? = null, private val presetCat: Long? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        val f = Form(ctx)
        val title = f.edit("Название *", existing?.title ?: "")
        f.label("Дата"); val date = DateBtn(ctx, existing?.date ?: presetDate ?: todayStr()); f.add(date)
        val allDay = f.check("Весь день", existing?.allDay ?: false)
        f.label("Начало"); val start = TimeBtn(ctx, existing?.start ?: "08:00"); f.add(start)
        f.label("Окончание"); val end = TimeBtn(ctx, existing?.end ?: "09:00"); f.add(end)
        val cat = f.spin(CAT_NAMES); cat.setSelection(((existing?.categoryId ?: presetCat ?: 1L) - 1).toInt())
        val rem = f.spin(REM_OPTIONS)
        val remInit = existing?.reminders?.toIntOrNull() ?: -1
        if (remInit > 0) rem.setSelection(REM_VALUES.indexOf(remInit) + 1)

        val rep = f.spin(REP_OPTIONS)
        val repDays = mutableListOf<Int>()
        val repDaysBtn = Button(ctx).apply { text = "Дни: не выбраны"; visibility = View.GONE }; f.add(repDaysBtn)
        fun updRepBtn() { repDaysBtn.text = if (repDays.isEmpty()) "Дни: не выбраны" else "Дни: " + repDays.map { DAY_LABELS[DAY_VALS.indexOf(it)] }.joinToString(", ") }
        rep.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                repDaysBtn.visibility = if (pos == 4) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        repDaysBtn.setOnClickListener {
            val checked = DAY_VALS.map { repDays.contains(it) }.toBooleanArray()
            AlertDialog.Builder(ctx).setTitle("Какие дни недели?").setMultiChoiceItems(DAY_LABELS.toTypedArray(), checked) { _, which, isC ->
                val dv = DAY_VALS[which]; if (isC) { if (!repDays.contains(dv)) repDays.add(dv) } else repDays.remove(dv)
            }.setPositiveButton("ОК") { _, _ -> updRepBtn() }.show()
        }
        existing?.let { e ->
            val pos = when (e.repeatType) { "DAILY" -> 1; "WEEKLY" -> 2; "MONTHLY" -> 3; "CUSTOM" -> 4; else -> 0 }
            rep.setSelection(pos)
            if (pos == 4) { repDays.addAll(e.repeatDays.split(",").mapNotNull { it.toIntOrNull() }); updRepBtn(); repDaysBtn.visibility = View.VISIBLE }
        }

        val note = f.edit("Заметка", existing?.note ?: "")
        val partIds = mutableListOf<Long>(); var memberIds = listOf<Long>(); var memberNames = listOf<String>()
        val partBtn = Button(ctx); f.add(partBtn); partBtn.text = "Участники: нет"
        scope.launch {
            val members = db.members().all().first()
            memberIds = members.map { it.id }; memberNames = members.map { it.name }
            if (existing != null) partIds.addAll(db.participants().membersOf(existing.id))
            val upd = { partBtn.text = if (partIds.isEmpty()) "Участники: нет" else "Участники: " + partIds.mapNotNull { id -> memberNames.getOrNull(memberIds.indexOf(id)) }.joinToString(", ") }
            upd()
            partBtn.setOnClickListener {
                val checked = memberIds.map { partIds.contains(it) }.toBooleanArray()
                AlertDialog.Builder(ctx).setTitle("Кто участвует").setMultiChoiceItems(memberNames.toTypedArray(), checked) { _, which, isC ->
                    val id = memberIds[which]; if (isC) { if (!partIds.contains(id)) partIds.add(id) } else partIds.remove(id)
                }.setPositiveButton("ОК") { _, _ -> upd() }.show()
            }
        }
        AlertDialog.Builder(ctx).setTitle(if (existing == null) "Новое событие" else "Событие").setView(f.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val t = title.text.toString().trim()
                if (t.isEmpty()) { Toast.makeText(ctx, "Введите название", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val s = start.value; val e2 = end.value
                if (!allDay.isChecked && minutesOf(e2) <= minutesOf(s)) { Toast.makeText(ctx, "Окончание позже начала", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val catId = (cat.selectedItemPosition + 1).toLong()
                val remVal = if (rem.selectedItemPosition == 0) "" else REM_VALUES[rem.selectedItemPosition - 1].toString()
                val repType = when (rep.selectedItemPosition) { 1 -> "DAILY"; 2 -> "WEEKLY"; 3 -> "MONTHLY"; 4 -> "CUSTOM"; else -> "NONE" }
                val repDaysStr = if (rep.selectedItemPosition == 4) repDays.joinToString(",") else ""
                scope.launch {
                    val doSave: suspend () -> Unit = {
                        val id = if (existing != null) {
                            db.events().update(existing.copy(title = t, date = date.value, start = s, end = e2, allDay = allDay.isChecked, categoryId = catId, note = note.text.toString(), reminders = remVal, repeatType = repType, repeatDays = repDaysStr)); existing.id
                        } else db.events().insert(Event(title = t, date = date.value, start = s, end = e2, allDay = allDay.isChecked, categoryId = catId, note = note.text.toString(), reminders = remVal, repeatType = repType, repeatDays = repDaysStr))
                        db.participants().clear(id)
                        partIds.forEach { db.participants().insert(EventParticipant(id, it)) }
                        if (remVal.isNotBlank() && repType == "NONE") ReminderScheduler.scheduleFor(db, "EVENT", id, t, date.value, if (allDay.isChecked) "" else s, remVal)
                        onSaved?.invoke()
                    }
                    val n = if (allDay.isChecked) 0 else db.events().overlaps(date.value, s, e2, existing?.id ?: -1)
                    if (n > 0) AlertDialog.Builder(ctx).setMessage("Пересекается с другим событием. Сохранить?").setPositiveButton("Да") { _, _ -> scope.launch { doSave() } }.setNegativeButton("Отмена", null).show()
                    else doSave()
                }
            }
            .setNegativeButton("Отмена", null).show()
    }
}

class NoteFormDialog(private val ctx: Context, private val existing: Note? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        val f = Form(ctx)
        val text = f.edit("Текст заметки *", existing?.text ?: "")
        f.label("Дата (необязательно)"); val date = DateBtn(ctx, existing?.date ?: ""); f.add(date)
        f.label("Время (необязательно)"); val time = TimeBtn(ctx, existing?.time ?: "18:00"); f.add(time)
        val rem = f.spin(listOf("Без напоминания", "В момент", "за 5 мин", "за 10 мин", "за 15 мин", "за 30 мин", "за 1 час"))
        AlertDialog.Builder(ctx).setTitle("Заметка").setView(f.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val t = text.text.toString().trim()
                if (t.isEmpty()) { Toast.makeText(ctx, "Введите текст", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val remVal = when (rem.selectedItemPosition) { 0 -> null; 1 -> 0; else -> listOf(5, 10, 15, 30, 60)[rem.selectedItemPosition - 2] }
                scope.launch {
                    if (existing != null) db.notes().update(existing.copy(text = t, date = if (date.value.isBlank()) "" else date.value, time = time.value, reminder = remVal))
                    else {
                        val id = db.notes().insert(Note(text = t, date = date.value, time = time.value, reminder = remVal))
                        if (remVal != null && date.value.isNotBlank()) ReminderScheduler.scheduleFor(db, "NOTE", id, t, date.value, time.value, remVal.toString())
                    }
                    onSaved?.invoke()
                }
            }.setNegativeButton("Отмена", null).show()
    }
}

fun showEventView(ctx: Context, id: Long) {
    val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
    scope.launch {
        val e = db.events().byId(id) ?: return@launch
        val box = ctx.colV().apply { setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(8)) }
        box.addView(ctx.tv(e.title, 18f, true))
        box.addView(ctx.tv(catName(e.categoryId), 14f, color = colorOf(e.categoryId)))
        box.addView(ctx.tv(e.date + if (e.allDay) " · весь день" else " · " + e.start + "–" + e.end, 14f))
        if (e.repeatType != "NONE") box.addView(ctx.tv("Повтор: " + (when (e.repeatType) { "DAILY" -> "ежедневно"; "WEEKLY" -> "еженедельно"; "MONTHLY" -> "ежемесячно"; else -> "дни: " + e.repeatDays }), 13f))
        val parts = db.participants().membersOf(id)
        if (parts.isNotEmpty()) {
            val names = parts.mapNotNull { db.members().byId(it)?.name }
            box.addView(ctx.tv("Участники: " + names.joinToString(", "), 13f))
        }
        if (e.note.isNotBlank()) box.addView(ctx.tv(e.note, 13f))
        AlertDialog.Builder(ctx).setView(box)
            .setPositiveButton("Изменить") { _, _ -> EventFormDialog(ctx, e).show() }
            .setNeutralButton("Удалить") { _, _ -> scope.launch { db.events().delete(id) } }
            .setNegativeButton("Закрыть", null).show()
    }
}
