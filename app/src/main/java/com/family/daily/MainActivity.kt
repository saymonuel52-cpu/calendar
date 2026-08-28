package com.family.daily

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.family.daily.ui.BookingDialog
import com.family.daily.ui.CalendarFragment
import com.family.daily.ui.EventFormDialog
import com.family.daily.ui.FamilyFragment
import com.family.daily.ui.NoteFormDialog
import com.family.daily.ui.NotesFragment
import com.family.daily.ui.SchoolFragment
import com.family.daily.ui.WorkFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private val ids = listOf(R.id.tab_calendar, R.id.tab_work, R.id.tab_family, R.id.tab_school, R.id.tab_notes)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pref = getSharedPreferences("app", MODE_PRIVATE)
        val crash = pref.getString("crash", null)
        if (crash != null) {
            pref.edit().remove("crash").apply()
            setContentView(buildCrashView(crash))
            return
        }
        setContentView(R.layout.activity_main)
        val pager = findViewById<ViewPager2>(R.id.pager)
        val nav = findViewById<BottomNavigationView>(R.id.nav)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val menu = findViewById<LinearLayout>(R.id.fabmenu)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> CalendarFragment()
                1 -> WorkFragment()
                2 -> FamilyFragment()
                3 -> SchoolFragment()
                else -> NotesFragment()
            }
        }
        nav.setOnItemSelectedListener { item -> pager.currentItem = ids.indexOf(item.itemId); true }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(p: Int) { nav.selectedItemId = ids[p] }
        })
        fab.setOnClickListener { menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        menu.findViewById<MaterialButton>(R.id.fab_event).setOnClickListener { menu.visibility = View.GONE; EventFormDialog(this).show() }
        menu.findViewById<MaterialButton>(R.id.fab_note).setOnClickListener { menu.visibility = View.GONE; NoteFormDialog(this).show() }
        menu.findViewById<MaterialButton>(R.id.fab_book).setOnClickListener { menu.visibility = View.GONE; BookingDialog(this).show() }
    }

    private fun buildCrashView(crash: String): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        root.addView(TextView(this).apply { text = "Прошлый запуск упал. Приложение в безопасном режиме. Скопируй текст ниже и пришли разработчику."; textSize = 16f })
        val tv = TextView(this).apply { text = crash; setTextIsSelectable(true); textSize = 12f }
        val sv = ScrollView(this).apply { addView(tv); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        root.addView(sv)
        root.addView(Button(this).apply {
            text = "Скопировать текст"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", crash))
                Toast.makeText(this@MainActivity, "Скопировано — вставь в чат", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(Button(this).apply { text = "Закрыть"; setOnClickListener { finish() } })
        return root
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
