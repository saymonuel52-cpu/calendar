package com.family.daily

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.AppDb
import com.family.daily.data.CalendarRepository
import com.family.daily.ui.addDaysStr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var pref: android.content.SharedPreferences
    private val db by lazy { AppDb.get(this) }

    private val rcExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val json = Backup.export(db)
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                pref.edit().putString("lastBackup", java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date())).apply()
                Toast.makeText(this@SettingsActivity, "Экспорт готов", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "Ошибка экспорта", Toast.LENGTH_SHORT).show() }
        }
    }
    private val rcImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
                AlertDialog.Builder(this@SettingsActivity).setMessage("Заменить текущие данные импортом?").setPositiveButton("Да") { _, _ ->
                    lifecycleScope.launch { Backup.import(db, text); Toast.makeText(this@SettingsActivity, "Импорт завершён", Toast.LENGTH_SHORT).show(); finish() }
                }.setNegativeButton("Отмена", null).show()
            } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "Файл не распознан", Toast.LENGTH_SHORT).show() }
        }
    }
    private val rcPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val doc = android.graphics.pdf.PdfDocument()
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                var page = doc.startPage(info)
                var canvas = page.canvas
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 22f }
                var y = 60f
                fun newPageIfNeeded() {
                    if (y > 800f) { doc.finishPage(page); page = doc.startPage(info); canvas = page.canvas; y = 60f }
                }
                canvas.drawText("РАСПИСАНИЕ СЕМЬИ", 40f, y, paint); y += 40f
                db.school().all().first().forEach { s ->
                    newPageIfNeeded()
                    val child = db.members().byId(s.childId)?.name ?: "Ребёнок"
                    canvas.drawText(child + ": Пн-Пт " + s.start + "-" + s.end, 40f, y, paint); y += 34f
                }
                y += 16f
                newPageIfNeeded()
                canvas.drawText("БЛИЖАЙШИЕ 7 ДНЕЙ:", 40f, y, paint); y += 34f
                val repo = CalendarRepository(db)
                for (i in 0..6) {
                    val ds = addDaysStr(i)
                    val items = repo.dayItems(ds).first()
                    newPageIfNeeded()
                    canvas.drawText(ds, 40f, y, paint); y += 30f
                    if (items.isEmpty()) { newPageIfNeeded(); canvas.drawText("   свободно", 40f, y, paint); y += 30f }
                    items.forEach { it2 ->
                        newPageIfNeeded()
                        canvas.drawText("   " + (if (it2.allDay) "весь день" else it2.start) + " " + it2.title, 40f, y, paint); y += 30f
                    }
                }
                doc.finishPage(page)
                contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                doc.close()
                Toast.makeText(this@SettingsActivity, "PDF сохранён — можно распечатать", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(this@SettingsActivity, "Ошибка PDF", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = getSharedPreferences("app", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val scroll = ScrollView(this); scroll.addView(root)
        setContentView(scroll)
        title = "Настройки"

        fun bump() { pref.edit().putInt("uiVersion", pref.getInt("uiVersion", 0) + 1).apply() }

        root.addView(tv("Доступность"))
        val simple = SwitchCompat(this).apply { text = "Простой режим (крупный шрифт, 3 вкладки, без кнопки +)"; isChecked = pref.getBoolean("simpleMode", false) }
        simple.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean("simpleMode", c).apply(); bump() }
        root.addView(simple)

        root.addView(tv("Модули (конструктор)"))
        listOf(
            "hideWork" to "Работа (вкладка)",
            "hideSchool" to "Школа (вкладка)",
            "hideNotes" to "Заметки (вкладка)",
            "hideShop" to "Покупки (в Семье)",
            "hideHealth" to "Здоровье (в Семье)",
            "hideChecklist" to "Чек-листы (в Семье)"
        ).forEach { (key, name) ->
            val cb = CheckBox(this).apply { text = "Модуль «" + name + "»"; isChecked = !pref.getBoolean(key, false) }
            cb.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean(key, !c).apply(); bump() }
            root.addView(cb)
        }
        val mc = SwitchCompat(this).apply { text = "Контакты из телефона"; isChecked = pref.getBoolean("modContacts", false) }
        mc.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean("modContacts", c).apply(); bump() }
        root.addView(mc)

        root.addView(tv("Резервные копии"))
        val autoBk = SwitchCompat(this).apply { text = "Автобэкап по воскресеньям"; isChecked = pref.getBoolean("autoBackupOn", true) }
        autoBk.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean("autoBackupOn", c).apply() }
        root.addView(autoBk)

        root.addView(tv("Утро"))
        val digestOn = SwitchCompat(this).apply { text = "Утренний дайджест (план дня пушем)"; isChecked = pref.getBoolean("digestOn", true) }
        digestOn.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean("digestOn", c).apply() }
        root.addView(digestOn)
        val dtOpts = listOf("07:00", "07:30", "08:00", "08:30")
        val digestTime = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, dtOpts)
            setSelection(dtOpts.indexOf(pref.getString("digestTime", "07:30")).coerceAtLeast(0))
        }
        digestTime.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p2: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                pref.edit().putString("digestTime", dtOpts[pos]).apply()
            }
            override fun onNothingSelected(p2: android.widget.AdapterView<*>?) {}
        }
        root.addView(digestTime)

        root.addView(tv("Внешний вид"))
        val colorPerson = SwitchCompat(this).apply { text = "Цвета по детям (а не по категориям)"; isChecked = pref.getBoolean("colorByPerson", false) }
        colorPerson.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean("colorByPerson", c).apply() }
        root.addView(colorPerson)
        val dark = SwitchCompat(this).apply { text = "Тёмная тема"; isChecked = pref.getBoolean("dark", false) }
        dark.setOnCheckedChangeListener { _, c ->
            pref.edit().putBoolean("dark", c).apply()
            AppCompatDelegate.setDefaultNightMode(if (c) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
        root.addView(dark)

        root.addView(tv("Первый день недели"))
        val fd = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, listOf("Понедельник", "Воскресенье"))
            setSelection(if (pref.getInt("firstDay", 1) == 1) 0 else 1)
        }
        fd.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                pref.edit().putInt("firstDay", if (pos == 0) 1 else 0).apply()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        root.addView(fd)

        root.addView(tv("Работа"))
        val buf = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, listOf("Без буфера", "Буфер 15 мин", "Буфер 30 мин", "Буфер 60 мин"))
            setSelection(when (pref.getInt("bufferMin", 0)) { 15 -> 1; 30 -> 2; 60 -> 3; else -> 0 })
        }
        buf.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                pref.edit().putInt("bufferMin", listOf(0, 15, 30, 60)[pos]).apply()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        root.addView(buf)
        root.addView(tv("Буфер — время на дорогу/подготовку между записями.", 12f))

        root.addView(tv("Данные"))
        root.addView(Button(this).apply {
            text = "💾 Экспорт (бэкап JSON)"
            setOnClickListener { rcExport.launch("family_planner_" + com.family.daily.ui.todayStr() + ".json") }
        })
        root.addView(Button(this).apply {
            text = "📥 Импорт из JSON"
            setOnClickListener { rcImport.launch(arrayOf("application/json", "*/*")) }
        })
        root.addView(Button(this).apply {
            text = "🖨 Расписание на холодильник (PDF)"
            setOnClickListener { rcPdf.launch("raspisanie_semji.pdf") }
        })
        val lb = pref.getString("lastBackup", null)
        if (lb != null) root.addView(tv("Последний бэкап: " + lb, 12f))
        root.addView(Button(this).apply {
            text = "🗑 Очистить все данные"
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity).setMessage("Удалить ВСЕ данные безвозвратно?").setPositiveButton("Да, удалить") { _, _ ->
                    lifecycleScope.launch {
                        db.events().clear(); db.participants().clearAll(); db.clients().clear(); db.services().clear(); db.notes().clear(); db.shop().clear(); db.school().clear()
                        Toast.makeText(this@SettingsActivity, "Данные удалены", Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton("Отмена", null).show()
            }
        })
        root.addView(tv("О приложении: Семейный ежедневник v1.5. Офлайн, данные только на телефоне.", 12f))
    }

    private fun tv(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size * fontScaleLocal(); setPadding(0, dp(8), 0, dp(4)) }
    private fun fontScaleLocal(): Float = if (pref.getBoolean("simpleMode", false)) 1.35f else 1f
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
