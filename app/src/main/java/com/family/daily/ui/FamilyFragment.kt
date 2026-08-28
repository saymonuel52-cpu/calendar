package com.family.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.AppDb
import com.family.daily.data.FamilyMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FamilyFragment : Fragment() {
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
        val chips = ctx.rowH()
        listOf("События", "Покупки", "Семья", "Здоровье").forEachIndexed { idx, name ->
            chips.addView(Button(ctx).apply { text = name; setOnClickListener { panel = idx; render() } })
        }
        root.addView(chips)
        when (panel) { 0 -> events(ctx, db); 1 -> shop(ctx, db); 2 -> members(ctx, db); 3 -> health(ctx, db) }
    }
    private suspend fun events(ctx: android.content.Context, db: AppDb) {
        val members = db.members().all().first()
        val list = db.events().byCat(2).first() + db.events().byCat(6).first()
        if (list.isEmpty()) root.addView(ctx.tv("Семейных событий нет.", 14f))
        list.sortedBy { it.date }.forEach { e ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(e.title, 15f, true))
            col.addView(ctx.tv(e.date + (if (e.allDay) " · весь день" else " · " + e.start), 13f))
            val parts = db.participants().membersOf(e.id).mapNotNull { id -> members.find { it.id == id }?.name }
            if (parts.isNotEmpty()) col.addView(ctx.tv("Идут: " + parts.joinToString(", "), 13f, color = colorOf(2)))
            col.addView(Button(ctx).apply { text = "Открыть"; setOnClickListener { showEventView(ctx, e.id) } })
            card.addView(col); root.addView(card)
        }
        root.addView(Button(ctx).apply { text = "+ Семейное событие"; setOnClickListener { EventFormDialog(ctx, presetCat = 2) { render() }.show() } })
    }
    private suspend fun shop(ctx: android.content.Context, db: AppDb) {
        val list = db.shop().all().first()
        val card = ctx.card(); val col = ctx.colV()
        if (list.isEmpty()) col.addView(ctx.tv("Список покупок пуст.", 14f))
        list.forEach { s ->
            val cb = CheckBox(ctx).apply {
                text = s.title; isChecked = s.bought
                setOnCheckedChangeListener { _, checked -> viewLifecycleOwner.lifecycleScope.launch { db.shop().setBought(s.id, checked) } }
            }
            col.addView(cb)
        }
        val addRow = ctx.rowH()
        val inp = EditText(ctx).apply { hint = "Новый товар"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        addRow.addView(inp)
        addRow.addView(Button(ctx).apply {
            text = "+"
            setOnClickListener {
                val t = inp.text.toString().trim(); if (t.isEmpty()) return@setOnClickListener
                viewLifecycleOwner.lifecycleScope.launch { db.shop().insert(com.family.daily.data.ShoppingItem(title = t)); render() }
            }
        })
        col.addView(addRow)
        col.addView(Button(ctx).apply { text = "Очистить купленные"; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db.shop().clearBought(); render() } } })
        card.addView(col); root.addView(card)
    }
    private suspend fun members(ctx: android.content.Context, db: AppDb) {
        val list = db.members().all().first()
        list.forEach { m ->
            val card = ctx.card(); val row = ctx.rowH()
            val t = ctx.colV()
            t.addView(ctx.tv(m.name, 15f, true))
            t.addView(ctx.tv(roleName(m.role) + (if (m.birthYear != null) " · " + m.birthYear else "") + (if (m.phone.isNotBlank()) " · " + m.phone else ""), 13f))
            t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(t)
            row.addView(Button(ctx).apply { text = "✏️"; setOnClickListener { MemberDialog(ctx, m) { render() }.show() } })
            card.addView(row); root.addView(card)
        }
        root.addView(Button(ctx).apply { text = "+ Член семьи"; setOnClickListener { MemberDialog(ctx) { render() }.show() } })
    }
    private suspend fun health(ctx: android.content.Context, db: AppDb) {
        val list = db.events().byCat(5).first()
        if (list.isEmpty()) root.addView(ctx.tv("Записей о здоровье нет. Прививки, врачи, «садик: документы».", 14f))
        list.forEach { e ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(e.title, 15f, true))
            col.addView(ctx.tv(e.date, 13f))
            col.addView(Button(ctx).apply { text = "Открыть"; setOnClickListener { showEventView(ctx, e.id) } })
            card.addView(col); root.addView(card)
        }
        root.addView(Button(ctx).apply { text = "+ Запись о здоровье"; setOnClickListener { EventFormDialog(ctx, presetCat = 5) { render() }.show() } })
    }
}

class MemberDialog(private val ctx: android.content.Context, private val existing: FamilyMember? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val f = Form(ctx)
        val name = f.edit("Имя *", existing?.name ?: "")
        val role = f.spin(ROLES.map { roleName(it) }); existing?.let { ROLES.indexOf(it.role).takeIf { i -> i >= 0 }?.let { role.setSelection(it) } }
        val phone = f.edit("Телефон", existing?.phone ?: "")
        val year = f.edit("Год рождения", existing?.birthYear?.toString() ?: "")
        AlertDialogBuilder(ctx).setTitle(if (existing == null) "Новый член семьи" else "Член семьи").setView(f.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { android.widget.Toast.makeText(ctx, "Введите имя", android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                scope.launch {
                    val y = year.text.toString().toIntOrNull()
                    if (existing != null) db.members().update(existing.copy(name = n, role = ROLES[role.selectedItemPosition], phone = phone.text.toString(), birthYear = y))
                    else db.members().insert(FamilyMember(name = n, role = ROLES[role.selectedItemPosition], phone = phone.text.toString(), birthYear = y))
                    onSaved?.invoke()
                }
            }.setNegativeButton("Отмена", null).show()
    }
    private fun AlertDialogBuilder(c: android.content.Context) = android.app.AlertDialog.Builder(c)
}
