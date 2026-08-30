package com.family.daily.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.family.daily.data.AppDb
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.fontScale(): Float = if (getSharedPreferences("app", 0).getBoolean("simpleMode", false)) 1.35f else 1f
fun colorOf(cat: Long): Int = Color.parseColor(when (cat) { 1L -> "#1E88E5"; 2L -> "#EC407A"; 3L -> "#43A047"; 4L -> "#FB8C00"; 5L -> "#8E24AA"; 6L -> "#8D6E63"; else -> "#757575" })
fun catName(cat: Long): String = when (cat) { 1L -> "Работа"; 2L -> "Семья"; 3L -> "Школа"; 4L -> "Личное"; 5L -> "Здоровье"; 6L -> "Питомец"; else -> "?" }
fun todayStr(): String { val c = Calendar.getInstance(); return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) }
fun addDaysStr(n: Int): String { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, n); return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) }
fun minutesOf(hm: String): Int { val p = hm.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }
fun fmtMin(min: Int): String = String.format("%02d:%02d", min / 60, min % 60)
fun dayOfWeekOf(date: String): Int { val p = date.split("-"); return Calendar.getInstance().apply { set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) }.get(Calendar.DAY_OF_WEEK) }
fun Fragment.toast(s: String) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show() }
fun Fragment.db(): AppDb = AppDb.get(requireContext())

val SOURCES = listOf("Telegram", "ВКонтакте", "WhatsApp", "сарафан", "знакомые", "реклама", "другое")
val ROLES = listOf("parent", "child", "grand", "nanny", "friend", "pet")
fun roleName(r: String): String = when (r) { "parent" -> "родитель"; "child" -> "ребёнок"; "grand" -> "помощник"; "nanny" -> "няня"; "friend" -> "знакомые"; "pet" -> "питомец"; else -> r }
val STATUSES = listOf("Записан", "Подтверждён", "Завершено", "Отменён")

fun showCrashDialog(ctx: Context, e: Throwable) {
    val trace = android.util.Log.getStackTraceString(e)
    val tv = TextView(ctx).apply { text = trace; setTextIsSelectable(true); textSize = 12f }
    val sv = ScrollView(ctx).apply { addView(tv) }
    AlertDialog.Builder(ctx).setTitle("Ошибка. Скопируй текст и пришли разработчику").setView(sv)
        .setPositiveButton("Скопировать") { _, _ ->
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            Toast.makeText(ctx, "Скопировано — вставь в чат", Toast.LENGTH_LONG).show()
        }.setNegativeButton("Закрыть", null).show()
}

fun Context.card(): MaterialCardView = MaterialCardView(this).apply {
    radius = dp(14).toFloat()
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    setContentPadding(dp(12), dp(12), dp(12), dp(12))
}
fun Context.tv(text: String, size: Float = 15f, bold: Boolean = false, color: Int = 0): TextView = TextView(this).apply {
    this.text = text; textSize = size * fontScale()
    if (bold) setTypeface(null, Typeface.BOLD)
    if (color != 0) setTextColor(color)
}
fun Context.bar(color: Int): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
    setBackgroundColor(color)
}
fun Context.rowH(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
fun Context.colV(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
