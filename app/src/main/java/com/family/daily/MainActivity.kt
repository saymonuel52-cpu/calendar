package com.family.daily

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val tabs = listOf("Календарь", "Работа", "Семья", "Школа", "Заметки")
    private val ids = listOf(R.id.tab_calendar, R.id.tab_work, R.id.tab_family, R.id.tab_school, R.id.tab_notes)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pager = findViewById<ViewPager2>(R.id.pager)
        val nav = findViewById<BottomNavigationView>(R.id.nav)
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(position: Int) = PlaceholderFragment.newInstance(tabs[position])
        }
        nav.setOnItemSelectedListener { item -> pager.currentItem = ids.indexOf(item.itemId); true }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(p: Int) { nav.selectedItemId = ids[p] }
        })
    }
}

class PlaceholderFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        TextView(requireContext()).apply { text = arguments?.getString("t"); textSize = 24f }
    companion object {
        fun newInstance(t: String) = PlaceholderFragment().apply { arguments = Bundle().apply { putString("t", t) } }
    }
}
