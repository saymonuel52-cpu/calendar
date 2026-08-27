package com.family.daily.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Database
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, FamilyMember::class, Client::class, Service::class, Event::class,
    EventParticipant::class, ShoppingItem::class, SchoolSchedule::class, WorkSchedule::class, Note::class,
    Template::class, HealthRecord::class, ReminderQueue::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun events(): EventDao
    abstract fun members(): MemberDao
    abstract fun clients(): ClientDao
    abstract fun services(): ServiceDao
    abstract fun participants(): ParticipantDao
    abstract fun shop(): ShopDao
    abstract fun school(): SchoolDao
    abstract fun workSchedule(): WorkScheduleDao
    abstract fun notes(): NoteDao
    abstract fun templates(): TemplateDao
    abstract fun health(): HealthDao
    abstract fun reminders(): ReminderDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "family.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) { seed(db) }
                }).build().also { inst = it }
        }
        private fun seed(db: SupportSQLiteDatabase) {
            listOf("Работа|#1E88E5", "Семья|#EC407A", "Школа|#43A047", "Личное|#FB8C00", "Здоровье|#8E24AA", "Питомец|#8D6E63")
                .forEach { s -> val p = s.split("|"); db.execSQL("INSERT INTO categories(name,color,isDefault) VALUES('" + p[0] + "','" + p[1] + "',1)") }
            db.execSQL("INSERT INTO family_members(name,role) VALUES('Мама','parent'),('Папа','parent'),('Бабушка','grand'),('Рекса','pet')")
            for (d in 0..6) db.execSQL("INSERT INTO work_schedule(day,start,end,off) VALUES(" + d + ",'10:00','18:00'," + (if (d == 0) 1 else 0) + ")")
        }
    }
}
