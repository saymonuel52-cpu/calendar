package com.family.daily

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.family.daily.ui.CalendarFragment
import com.family.daily.ui.EventFormDialog
import com.family.daily.ui.NoteFormDialog
import com.family.daily.ui.NotesFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private val ids = listOf(R.id.tab_calendar, R.id.tab_work, R.id.tab_family, R.id.tab_school, R.id.tab_notes)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pager = findViewById<ViewPager2>(R.id.pager)
        val nav = findViewById<BottomNavigationView>(R.id.nav)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val menu = findViewById<LinearLayout>(R.id.fabmenu)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> CalendarFragment()
                4 -> NotesFragment()
                else -> PlaceholderFragment.newInstance(listOf("Календарь", "Работа", "Семья", "Школа", "Заметки")[position])
            }
        }
        nav.setOnItemSelectedListener { item -> pager.currentItem = ids.indexOf(item.itemId); true }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(p: Int) { nav.selectedItemId = ids[p] }
        })
        fab.setOnClickListener { menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        menu.findViewById<MaterialButton>(R.id.fab_event).setOnClickListener { menu.visibility = View.GONE; EventFormDialog(this).show() }
        menu.findViewById<MaterialButton>(R.id.fab_note).setOnClickListener { menu.visibility = View.GONE; NoteFormDialog(this).show() }
        menu.findViewById<MaterialButton>(R.id.fab_book).setOnClickListener { menu.visibility = View.GONE; Toast.makeText(this, "Записи клиентов — в дропе 4", Toast.LENGTH_SHORT).show() }
    }
}

class PlaceholderFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        TextView(requireContext()).apply { text = arguments?.getString("t"); textSize = 24f }
    companion object {
        fun newInstance(t: String) = PlaceholderFragment().apply { arguments = Bundle().apply { putString("t", t) } }
    }
}
