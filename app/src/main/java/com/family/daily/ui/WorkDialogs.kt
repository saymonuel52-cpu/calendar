package com.family.daily.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.family.daily.ReminderScheduler
import com.family.daily.data.AppDb
import com.family.daily.data.Client
import com.family.daily.data.Event
import com.family.daily.data.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

suspend fun freeSlots(db: AppDb, date: String, duration: Int): List<String> {
    val dow = dayOfWeekOf(date) - 1
    val w = db.workSchedule().all().first().find { it.day == dow } ?: return emptyList()
    if (w.off) return emptyList()
    val busy = db.events().workOn(date)
    val nowMin = if (date == todayStr()) { val c = java.util.Calendar.getInstance(); c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE) } else 0
    val res = mutableListOf<String>()
    var t = minutesOf(w.start)
    while (t + duration <= minutesOf(w.end)) {
        val s = t; val e = t + duration
        if (s >= nowMin && busy.none { b -> minutesOf(b.start) < e && s < minutesOf(b.end) }) res.add(fmtMin(s) + "–" + fmtMin(e))
        t += 60
    }
    return res
}

class BookingDialog(private val ctx: Context, private val clientId: Long? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val clients = db.clients().all().first()
            if (clients.isEmpty()) { Toast.makeText(ctx, "Сначала добавьте клиента", Toast.LENGTH_SHORT).show(); ClientDialog(ctx) { BookingDialog(ctx, onSaved = onSaved).show() }.show(); return@launch }
            val services = db.services().all().first()
            if (services.isEmpty()) { Toast.makeText(ctx, "Сначала добавьте услугу", Toast.LENGTH_SHORT).show(); ServiceDialog(ctx).show(); return@launch }
            val f = Form(ctx)
            val cl = f.spin(clients.map { it.name })
            clientId?.let { id -> clients.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { cl.setSelection(it) } }
            val sv = f.spin(services.map { it.name + " · " + it.duration + " мин · " + it.price.toInt() + " ₽" })
            f.label("Дата"); val date = DateBtn(ctx, todayStr()); f.add(date)
            f.label("Свободные слоты (рабочие часы)"); val slotsBox = ctx.colV(); f.add(slotsBox)
            val rem = f.spin(REM_OPTIONS)
            var chosen: String? = null
            fun refreshSlots() {
                scope.launch {
                    chosen = null
                    slotsBox.removeAllViews()
                    val dur = services[sv.selectedItemPosition].duration
                    val slots = freeSlots(db, date.value, dur)
                    if (slots.isEmpty()) slotsBox.addView(ctx.tv("Нет свободных слотов (выходной или всё занято)", 13f))
                    slots.forEach { s ->
                        slotsBox.addView(Button(ctx).apply {
                            text = s
                            setOnClickListener { chosen = s; slotsBox.forEachChild { b -> (b as Button).alpha = 0.6f }; alpha = 1f }
                        })
                    }
                }
            }
            sv.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { refreshSlots() }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            })
            date.setOnClickListener { refreshSlots() }
            refreshSlots()
            AlertDialog.Builder(ctx).setTitle("Запись клиента").setView(f.root)
                .setPositiveButton("Сохранить") { _, _ ->
                    val slot = chosen
                    if (slot == null) { Toast.makeText(ctx, "Выберите свободный слот", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                    val c = clients[cl.selectedItemPosition]; val s = services[sv.selectedItemPosition]
                    val parts = slot.split("–")
                    val remVal = if (rem.selectedItemPosition == 0) "" else REM_VALUES[rem.selectedItemPosition - 1].toString()
                    scope.launch {
                        val id = db.events().insert(Event(title = c.name + " — " + s.name, date = date.value, start = parts[0], end = parts[1], categoryId = 1, clientId = c.id, serviceId = s.id, price = s.price, status = "Записан", sourceType = "WORK", reminders = remVal))
                        if (remVal.isNotBlank()) ReminderScheduler.scheduleFor(db, "EVENT", id, c.name + " — " + s.name, date.value, parts[0], remVal)
                        Toast.makeText(ctx, "Записано: " + c.name + ", " + date.value + ", " + slot, Toast.LENGTH_LONG).show()
                        onSaved?.invoke()
                    }
                }
                .setNegativeButton("Отмена", null).show()
        }
    }
    private fun LinearLayout.forEachChild(action: (android.view.View) -> Unit) { for (i in 0 until childCount) action(getChildAt(i)) }
}

class ClientDialog(private val ctx: Context, private val existing: Client? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        val f = Form(ctx)
        val name = f.edit("Имя *", existing?.name ?: "")
        val phone = f.edit("Телефон", existing?.phone ?: "")
        val src = f.spin(SOURCES); existing?.let { SOURCES.indexOf(it.source).takeIf { i -> i >= 0 }?.let { src.setSelection(it) } }
        val note = f.edit("Заметка (любит после 18:00…)", existing?.note ?: "")
        AlertDialog.Builder(ctx).setTitle(if (existing == null) "Новый клиент" else "Клиент").setView(f.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { Toast.makeText(ctx, "Введите имя", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                scope.launch {
                    if (existing != null) db.clients().update(existing.copy(name = n, phone = phone.text.toString(), source = src.selectedItem.toString(), note = note.text.toString()))
                    else db.clients().insert(Client(name = n, phone = phone.text.toString(), source = src.selectedItem.toString(), note = note.text.toString()))
                    onSaved?.invoke()
                }
            }.setNegativeButton("Отмена", null).show()
    }
}

class ServiceDialog(private val ctx: Context, private val existing: Service? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = CoroutineScope(Dispatchers.Main)
        val f = Form(ctx)
        val name = f.edit("Название *", existing?.name ?: "")
        val dur = f.edit("Длительность, мин", (existing?.duration ?: 60).toString())
        val price = f.edit("Цена, ₽", (existing?.price ?: 0.0).toInt().toString())
        AlertDialog.Builder(ctx).setTitle(if (existing == null) "Новая услуга" else "Услуга").setView(f.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { Toast.makeText(ctx, "Введите название", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                scope.launch {
                    val d = dur.text.toString().toIntOrNull() ?: 60; val p = price.text.toString().toDoubleOrNull() ?: 0.0
                    if (existing != null) db.services().update(existing.copy(name = n, duration = d, price = p))
                    else db.services().insert(Service(name = n, duration = d, price = p))
                    onSaved?.invoke()
                }
            }.setNegativeButton("Отмена", null).show()
    }
}
