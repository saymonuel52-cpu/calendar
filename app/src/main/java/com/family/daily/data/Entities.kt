package com.family.daily.data

import androidx.room.*

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val color: String, val icon: String = "", val isDefault: Boolean = true
)

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val role: String, val phone: String = "", val birthYear: Int? = null, val color: String = ""
)

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val phone: String = "", val source: String = "", val email: String = "", val address: String = "", val note: String = ""
)

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val duration: Int = 60, val price: Double = 0.0
)

@Entity(tableName = "events", indices = [Index("date"), Index("categoryId"), Index("clientId")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val date: String, val start: String = "", val end: String = "",
    val allDay: Boolean = false, val categoryId: Long = 1, val location: String = "", val note: String = "",
    val reminders: String = "", val repeatType: String = "NONE", val repeatDays: String = "", val isAlarm: Boolean = false,
    val status: String = "", val clientId: Long? = null, val serviceId: Long? = null, val price: Double? = null,
    val organizerId: Long? = null, val sourceType: String = "EVENT", val silent: Boolean = false
)

@Entity(tableName = "event_participants", primaryKeys = ["eventId", "memberId"])
data class EventParticipant(val eventId: Long, val memberId: Long)

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val category: String = "", val priority: Int = 0, val qty: String = "", val bought: Boolean = false
)

@Entity(tableName = "school_schedule")
data class SchoolSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val start: String,
    val end: String,
    val enabled: Boolean = true,
    val days: String = "2,3,4,5,6",
    val title: String = "Школа",
    val categoryId: Long = 3
)

@Entity(tableName = "work_schedule")
data class WorkSchedule(
    @PrimaryKey val day: Int, val start: String = "10:00", val end: String = "18:00", val off: Boolean = false
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String, val date: String = "", val time: String = "", val reminder: Int? = null,
    val color: String = "#FBC02D", val done: Boolean = false, val repeatType: String = "NONE", val repeatDays: String = ""
)

@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val dayOfWeek: Int, val start: String, val end: String, val categoryId: Long, val silent: Boolean = true
)

@Entity(tableName = "health_records")
data class HealthRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long, val type: String, val title: String, val date: String, val reminder: Int? = null
)

@Entity(tableName = "reminder_queue")
data class ReminderQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String, val sourceId: Long, val title: String, val fireAt: Long, val fired: Boolean = false
)

@Entity(tableName = "checklists")
data class Checklist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String
)

@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val title: String,
    val doneDate: String = ""
)

@Entity(tableName = "repeat_exceptions", indices = [Index("eventId")])
data class RepeatException(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val date: String
)
