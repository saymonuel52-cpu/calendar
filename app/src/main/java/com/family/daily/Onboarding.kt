package com.family.daily

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.SwitchCompat
import com.family.daily.data.AppDb
import com.family.daily.data.FamilyMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Onboarding {
    fun showIfNeeded(ctx: Context) {
        val pref = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (pref.getBoolean("onboardingDone", false)) return
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(4)) }
        root.addView(android.widget.TextView(ctx).apply { text = "Добро пожаловать! Пара вопросов, чтобы настроить приложение под вас."; textSize = 15f })
        val g1 = RadioGroup(ctx)
        listOf("мастер (шугаринг, маникюр…)", "мама", "бабушка", "другое").forEach {
            g1.addView(RadioButton(ctx).apply { text = it })
        }
        root.addView(android.widget.TextView(ctx).apply { text = "Кто вы?"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD) })
        root.addView(g1)
        val kidsInput = EditText(ctx).apply { hint = "Сколько детей? (0, если нет)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        root.addView(android.widget.TextView(ctx).apply { text = "Дети"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD) })
        root.addView(kidsInput)
        val simple = SwitchCompat(ctx).apply { text = "Простой режим (крупный шрифт, 3 вкладки)"; isChecked = false }
        root.addView(simple)
        AlertDialog.Builder(ctx).setTitle("Настройка").setView(root)
            .setCancelable(false)
            .setPositiveButton("Готово") { _, _ ->
                val whoIdx = g1.indexOfChild(g1.findViewById(g1.checkedRadioButtonId))
                val pref = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                val ed = pref.edit()
                when (whoIdx) {
                    0 -> { /* мастер */ /* всё включено по умолчанию */ }
                    1 -> { ed.putBoolean("hideWork", true) }
                    2 -> { ed.putBoolean("hideWork", true); ed.putBoolean("hideSchool", true); simple.isChecked = true }
                    3 -> { /* другое */ }
                }
                ed.putBoolean("simpleMode", simple.isChecked)
                ed.putBoolean("onboardingDone", true)
                ed.putInt("uiVersion", pref.getInt("uiVersion", 0) + 1)
                ed.apply()
                val kids = kidsInput.text.toString().toIntOrNull() ?: 0
                if (kids > 0) {
                    val db = AppDb.get(ctx)
                    CoroutineScope(Dispatchers.IO).launch {
                        for (i in 1..kids) {
                            db.members().insert(FamilyMember(name = "Ребёнок $i", role = "child"))
                        }
                    }
                }
                android.widget.Toast.makeText(ctx, "Готово! Настройте позже в ⚙️.", android.widget.Toast.LENGTH_LONG).show()
            }.show()
    }
    private fun dp(v: Int) = (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
