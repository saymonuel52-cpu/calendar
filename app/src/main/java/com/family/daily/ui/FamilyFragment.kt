package com.family.daily.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.family.daily.data.AppDb
import com.family.daily.data.FamilyMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FamilyFragment : Fragment() {
    private var panel = "ev"
    private lateinit var root: LinearLayout
    private lateinit var contentBox: LinearLayout
    private lateinit var contactsList: LinearLayout

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadPhoneContacts("") else if (isAdded) toast("Нет доступа к контактам")
    }

    private fun pref() = requireContext().getSharedPreferences("app", 0)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        root = ctx.colV().apply { setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(96)) }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        contentBox = ctx.colV()
        return scroll
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        root.addView(HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            addView(ctx2().rowH().apply { id = View.generateViewId(); chipsRow = this })
        })
        root.addView(contentBox)
        render()
    }

    private lateinit var chipsRow: LinearLayout
    private fun ctx2() = requireContext()

    private fun render() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            try { renderInner() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private suspend fun renderInner() {
        val ctx = requireContext()
        chipsRow.removeAllViews()
        val items = mutableListOf<Pair<String, String>>()
        items.add("ev" to "События")
        if (!pref().getBoolean("hideShop", false)) items.add("shop" to "Покупки")
        items.add("mem" to "Семья")
        if (!pref().getBoolean("hideHealth", false)) items.add("hp" to "Здоровье")
        if (pref().getBoolean("modContacts", false)) items.add("contacts" to "Контакты")
        if (!pref().getBoolean("hideChecklist", false)) items.add("chk" to "Чек-листы")
        items.forEach { (key, name) ->
            chipsRow.addView(Button(ctx).apply {
                text = name; minWidth = 0; minimumWidth = 0
                setOnClickListener { panel = key; showPanel() }
            })
        }
        showPanel()
    }

    private fun showPanel() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ctx = requireContext(); val db = db()
                contentBox.removeAllViews()
                when (panel) {
                    "ev" -> events(ctx, db)
                    "shop" -> shop(ctx, db)
                    "mem" -> members(ctx, db)
                    "hp" -> health(ctx, db)
                    "contacts" -> contactsPanel(ctx)
                    "chk" -> checklists(ctx, db)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
    }

    private suspend fun events(ctx: android.content.Context, db: AppDb) {
        val members = db.members().all().first()
        val list = db.events().byCat(2).first() + db.events().byCat(6).first()
        if (list.isEmpty()) contentBox.addView(ctx.tv("Семейных событий нет.", 14f))
        list.sortedBy { it.date }.forEach { e ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(e.title, 15f, true))
            col.addView(ctx.tv(e.date + (if (e.allDay) " · весь день" else " · " + e.start), 13f))
            val parts = db.participants().membersOf(e.id).mapNotNull { id -> members.find { it.id == id }?.name }
            if (parts.isNotEmpty()) col.addView(ctx.tv("Идут: " + parts.joinToString(", "), 13f, color = colorOf(2)))
            col.addView(Button(ctx).apply { text = "Открыть"; minWidth = 0; minimumWidth = 0; setOnClickListener { showEventView(ctx, e.id) } })
            card.addView(col); contentBox.addView(card)
        }
        contentBox.addView(Button(ctx).apply { text = "+ Семейное событие"; setOnClickListener { EventFormDialog(ctx, presetCat = 2) { render() }.show() } })
    }

    private suspend fun shop(ctx: android.content.Context, db: AppDb) {
        val list = db.shop().all().first()
        val card = ctx.card(); val col = ctx.colV()
        if (list.isEmpty()) col.addView(ctx.tv("Список покупок пуст.", 14f))
        list.forEach { s ->
            col.addView(android.widget.CheckBox(ctx).apply {
                text = s.title; isChecked = s.bought
                setOnCheckedChangeListener { _, checked -> viewLifecycleOwner.lifecycleScope.launch { db.shop().setBought(s.id, checked) } }
            })
        }
        val addRow = ctx.rowH()
        val inp = EditText(ctx).apply { hint = "Новый товар"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        addRow.addView(inp)
        addRow.addView(Button(ctx).apply {
            text = "+"; minWidth = 0; minimumWidth = 0
            setOnClickListener {
                val t = inp.text.toString().trim(); if (t.isEmpty()) return@setOnClickListener
                viewLifecycleOwner.lifecycleScope.launch { db.shop().insert(com.family.daily.data.ShoppingItem(title = t)); showPanel() }
            }
        })
        col.addView(addRow)
        col.addView(Button(ctx).apply { text = "Очистить купленные"; minWidth = 0; minimumWidth = 0; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db.shop().clearBought(); showPanel() } } })
        card.addView(col); contentBox.addView(card)
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
            row.addView(Button(ctx).apply { text = "✏️"; minWidth = 0; minimumWidth = 0; setOnClickListener { MemberDialog(ctx, m) { render() }.show() } })
            card.addView(row); contentBox.addView(card)
        }
        contentBox.addView(Button(ctx).apply { text = "+ Член семьи"; setOnClickListener { MemberDialog(ctx) { render() }.show() } })
    }

    private suspend fun health(ctx: android.content.Context, db: AppDb) {
        val list = db.events().byCat(5).first()
        if (list.isEmpty()) contentBox.addView(ctx.tv("Записей о здоровье нет. Прививки, врачи, «садик: документы».", 14f))
        list.forEach { e ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(e.title, 15f, true))
            col.addView(ctx.tv(e.date, 13f))
            col.addView(Button(ctx).apply { text = "Открыть"; minWidth = 0; minimumWidth = 0; setOnClickListener { showEventView(ctx, e.id) } })
            card.addView(col); contentBox.addView(card)
        }
        contentBox.addView(Button(ctx).apply { text = "+ Запись о здоровье"; setOnClickListener { EventFormDialog(ctx, presetCat = 5) { render() }.show() } })
    }

    private fun contactsPanel(ctx: android.content.Context) {
        contentBox.removeAllViews()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contentBox.addView(ctx.tv("Модулю нужен доступ к контактам телефона.", 14f))
            contentBox.addView(Button(ctx).apply { text = "Разрешить доступ"; setOnClickListener { permLauncher.launch(Manifest.permission.READ_CONTACTS) } })
            return
        }
        val search = EditText(ctx).apply { hint = "Поиск по имени или номеру" }
        contentBox.addView(search)
        contactsList = ctx.colV()
        contentBox.addView(contactsList)
        search.doAfterTextChanged { loadPhoneContacts(it?.toString() ?: "") }
        loadPhoneContacts("")
    }

    private fun loadPhoneContacts(filter: String) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val list = mutableListOf<Pair<String, String>>()
                val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
                ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cur ->
                    val seen = HashSet<String>()
                    while (cur.moveToNext()) {
                        val name = cur.getString(0) ?: continue
                        val num = cur.getString(1) ?: continue
                        val key = name + num.replace(" ", "")
                        if (seen.contains(key)) continue
                        seen.add(key)
                        if (filter.isBlank() || name.contains(filter, true) || num.contains(filter)) list.add(name to num)
                    }
                }
                withContext(Dispatchers.Main) { if (isAdded) renderContacts(ctx, list) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { if (isAdded) showCrashDialog(ctx, e) } }
        }
    }

    private fun renderContacts(ctx: android.content.Context, list: List<Pair<String, String>>) {
        contactsList.removeAllViews()
        if (list.isEmpty()) { contactsList.addView(ctx.tv("Не найдено", 13f)); return }
        viewLifecycleOwner.lifecycleScope.launch {
            val members = db().members().all().first()
            val memberPhones = members.map { it.phone.replace(Regex("[^0-9+]"), "") }.filter { it.isNotBlank() }.toSet()
            list.take(100).forEach { (name, num) ->
                val row = ctx.rowH()
                val col = ctx.colV()
                col.addView(ctx.tv(name, 15f, true))
                col.addView(ctx.tv(num, 12f))
                col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(col)
                val clean = num.replace(Regex("[^0-9+]"), "")
                if (memberPhones.contains(clean)) row.addView(ctx.tv("✓ в семье", 12f, color = Color.parseColor("#43A047")))
                row.addView(Button(ctx).apply {
                    text = "📞"; minWidth = 0; minimumWidth = 0
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + clean))) } catch (_: Exception) {} }
                })
                if (!memberPhones.contains(clean)) row.addView(Button(ctx).apply {
                    text = "→ в семью"; minWidth = 0; minimumWidth = 0
                    setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            db().members().insert(FamilyMember(name = name, role = "friend", phone = num))
                            toast("Добавлен(а) в семью: " + name)
                            loadPhoneContacts("")
                        }
                    }
                })
                contactsList.addView(row)
            }
            contactsList.addView(ctx.tv("Показаны первые 100. Используй поиск.", 11f))
        }
    }
    private suspend fun checklists(ctx: android.content.Context, db: AppDb) {
        val lists = db.checklists().all().first()
        if (lists.isEmpty()) contentBox.addView(ctx.tv("Чек-листов нет. Создайте «Утро», «Вечер», «Сборы в школу».", 14f))
        val today = todayStr()
        lists.forEach { cl ->
            val card = ctx.card(); val col = ctx.colV()
            col.addView(ctx.tv(cl.title, 15f, true))
            val items = db.checklistItems().byList(cl.id).first()
            items.forEach { it ->
                col.addView(android.widget.CheckBox(ctx).apply {
                    text = it.title; isChecked = it.doneDate == today
                    setOnCheckedChangeListener { _, checked -> viewLifecycleOwner.lifecycleScope.launch { db.checklistItems().setDone(it.id, if (checked) today else "") } }
                })
            }
            val addRow = ctx.rowH()
            val inp = android.widget.EditText(ctx).apply { hint = "Новый пункт"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            addRow.addView(inp)
            addRow.addView(Button(ctx).apply {
                text = "+"; minWidth = 0; minimumWidth = 0
                setOnClickListener {
                    val t = inp.text.toString().trim(); if (t.isEmpty()) return@setOnClickListener
                    viewLifecycleOwner.lifecycleScope.launch { db.checklistItems().insert(com.family.daily.data.ChecklistItem(checklistId = cl.id, title = t)); showPanel() }
                }
            })
            addRow.addView(Button(ctx).apply { text = "🗑"; minWidth = 0; minimumWidth = 0; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db.checklists().delete(cl.id); showPanel() } } })
            col.addView(addRow)
            card.addView(col); contentBox.addView(card)
        }
        contentBox.addView(Button(ctx).apply {
            text = "+ Чек-лист"
            setOnClickListener {
                val et = android.widget.EditText(ctx).apply { hint = "Название" }
                android.app.AlertDialog.Builder(ctx).setView(et).setPositiveButton("Создать") { _, _ ->
                    val t = et.text.toString().trim(); if (t.isEmpty()) return@setPositiveButton
                    viewLifecycleOwner.lifecycleScope.launch { db.checklists().insert(com.family.daily.data.Checklist(title = t)); showPanel() }
                }.setNegativeButton("Отмена", null).show()
            }
        })
    }
}

class MemberDialog(private val ctx: android.content.Context, private val existing: FamilyMember? = null, private val presetRole: String? = null, private val onSaved: (() -> Unit)? = null) {
    fun show() {
        val db = AppDb.get(ctx); val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val f = Form(ctx)
        val name = f.edit("Имя *", existing?.name ?: "")
        val role = f.spin(ROLES.map { roleName(it) }); (existing?.role ?: presetRole)?.let { ROLES.indexOf(it).takeIf { i -> i >= 0 }?.let { role.setSelection(it) } }
        val phone = f.edit("Телефон", existing?.phone ?: "")
        val year = f.edit("Год рождения", existing?.birthYear?.toString() ?: "")
        android.app.AlertDialog.Builder(ctx).setTitle(if (existing == null) "Новый член семьи" else "Член семьи").setView(f.root)
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
}
