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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = getSharedPreferences("app", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val scroll = ScrollView(this); scroll.addView(root)
        setContentView(scroll)
        title = "Настройки"

        fun bump() { pref.edit().putInt("uiVersion", pref.getInt("uiVersion", 0) + 1).apply() }

        root.addView(tv("Внешний вид"))
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
        root.addView(tv("Буфер — время на дорогу/подготовку между записями. Учитывается в свободных слотах.", 12f))

        root.addView(tv("Вкладки (скрыть ненужное)"))
        listOf("hideWork" to "Работа", "hideSchool" to "Школа", "hideNotes" to "Заметки").forEach { (key, name) ->
            val cb = CheckBox(this).apply { text = "Показывать «" + name + "»"; isChecked = !pref.getBoolean(key, false) }
            cb.setOnCheckedChangeListener { _, c -> pref.edit().putBoolean(key, !c).apply(); bump() }
            root.addView(cb)
        }

        root.addView(tv("Данные"))
        root.addView(Button(this).apply {
            text = "💾 Экспорт (бэкап JSON)"
            setOnClickListener { rcExport.launch("family_planner_" + com.family.daily.ui.todayStr() + ".json") }
        })
        root.addView(Button(this).apply {
            text = "📥 Импорт из JSON"
            setOnClickListener { rcImport.launch(arrayOf("application/json", "*/*")) }
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

    private fun tv(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setPadding(0, dp(8), 0, dp(4)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
