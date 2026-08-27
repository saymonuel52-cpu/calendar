package com.family.daily.ui

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NotesFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        val root = ctx.colV().apply { setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(96)) }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                db().notes().all().collect { list ->
                    root.removeAllViews()
                    root.addView(ctx.tv("Заметки", 18f, true))
                    if (list.isEmpty()) root.addView(ctx.tv("Заметок пока нет. Нажмите +, чтобы добавить.", 14f))
                    list.forEach { n ->
                        val card = ctx.card(); val col = ctx.colV()
                        col.addView(ctx.tv(n.text, 15f).apply { if (n.done) { paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG; alpha = 0.6f } })
                        if (n.date.isNotBlank()) col.addView(ctx.tv(n.date + (if (n.time.isNotBlank()) " " + n.time else "") + " 🔔", 12f))
                        val btns = ctx.rowH()
                        btns.addView(Button(ctx).apply { text = if (n.done) "Вернуть" else "Готово"; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db().notes().update(n.copy(done = !n.done)) } } })
                        btns.addView(Button(ctx).apply { text = "Изменить"; setOnClickListener { NoteFormDialog(ctx, n).show() } })
                        btns.addView(Button(ctx).apply { text = "Удалить"; setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { db().notes().delete(n.id) } } })
                        col.addView(btns); card.addView(col); root.addView(card)
                    }
                    root.addView(Button(ctx).apply { text = "+ Заметка"; setOnClickListener { NoteFormDialog(ctx).show() } })
                }
            } catch (e: Exception) { if (isAdded) showCrashDialog(requireContext(), e) }
        }
        return scroll
    }
}
