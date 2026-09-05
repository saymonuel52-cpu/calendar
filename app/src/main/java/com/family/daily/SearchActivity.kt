package com.family.daily

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.family.daily.ui.withFontScale
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.AppDb
import com.family.daily.ui.EventFormDialog
import com.family.daily.ui.NoteFormDialog
import com.family.daily.ui.card
import com.family.daily.ui.colV
import com.family.daily.ui.dp
import com.family.daily.ui.rowH
import com.family.daily.ui.tv
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class SearchActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(newBase.withFontScale()) }
    private lateinit var input: EditText
    private lateinit var results: LinearLayout
    private var job: Job? = null
    private val db by lazy { AppDb.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Поиск"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        input = EditText(this).apply { hint = "Имя, слово, телефон…" }
        root.addView(input)
        val scroll = ScrollView(this)
        results = colV()
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { schedule() }
        })
        results.addView(tv("Введите запрос — найдём в событиях, клиентах, семье, заметках.", 13f))
    }

    private fun schedule() {
        job?.cancel()
        val q = input.text.toString().trim()
        job = lifecycleScope.launch {
            delay(300)
            if (q.length < 2) { renderEmpty(); return@launch }
            runSearch(q.lowercase())
        }
    }

    private fun renderEmpty() { results.removeAllViews(); results.addView(tv("Введите минимум 2 симвора.", 13f)) }

    private suspend fun runSearch(q: String) {
        val ctx = this
        results.removeAllViews()
        var any = false
        val events = db.events().between("0000-01-01", "9999-12-31").first().filter { e -> e.title.lowercase().contains(q) || e.note.lowercase().contains(q) }
        if (events.isNotEmpty()) {
            any = true
            results.addView(tv("События (" + events.size + ")", 14f, true))
            events.take(20).forEach { e ->
                val card = ctx.card(); val r = ctx.rowH()
                val t = ctx.colV()
                t.addView(tv(e.title, 14f, true))
                t.addView(tv(e.date + " " + e.start, 12f))
                t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                r.addView(t)
                r.addView(Button(ctx).apply { text = "→"; minWidth = 0; minimumWidth = 0; setOnClickListener { EventFormDialog(ctx, e).show() } })
                card.addView(r); results.addView(card)
            }
        }
        val clients = db.clients().all().first().filter { c -> c.name.lowercase().contains(q) || c.phone.contains(q) || c.note.lowercase().contains(q) }
        if (clients.isNotEmpty()) {
            any = true
            results.addView(tv("Клиенты (" + clients.size + ")", 14f, true))
            clients.take(10).forEach { c ->
                val card = ctx.card()
                card.addView(tv(c.name, 14f, true))
                card.addView(tv(c.phone + " · " + c.source, 12f))
                results.addView(card)
            }
        }
        val members = db.members().all().first().filter { m -> m.name.lowercase().contains(q) || m.phone.contains(q) }
        if (members.isNotEmpty()) {
            any = true
            results.addView(tv("Семья (" + members.size + ")", 14f, true))
            members.take(10).forEach { m ->
                val card = ctx.card()
                card.addView(tv(m.name, 14f, true))
                card.addView(tv(m.role + " · " + m.phone, 12f))
                results.addView(card)
            }
        }
        val notes = db.notes().all().first().filter { n -> n.text.lowercase().contains(q) }
        if (notes.isNotEmpty()) {
            any = true
            results.addView(tv("Заметки (" + notes.size + ")", 14f, true))
            notes.take(20).forEach { n ->
                val card = ctx.card(); val r = ctx.rowH()
                val t = ctx.colV()
                t.addView(tv(n.text.take(60) + (if (n.text.length > 60) "…" else ""), 13f))
                t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                r.addView(t)
                r.addView(Button(ctx).apply { text = "→"; minWidth = 0; minimumWidth = 0; setOnClickListener { NoteFormDialog(ctx, n).show() } })
                card.addView(r); results.addView(card)
            }
        }
        if (!any) results.addView(tv("Ничего не найдено", 13f))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
