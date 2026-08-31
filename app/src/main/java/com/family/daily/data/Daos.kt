package com.family.daily.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id") fun all(): Flow<List<Category>>
    @Insert suspend fun insert(c: Category): Long
}
@Dao interface EventDao {
    @Query("SELECT * FROM events WHERE date = :d ORDER BY start") fun onDay(d: String): Flow<List<Event>>
    @Query("SELECT * FROM events WHERE date BETWEEN :f AND :t ORDER BY date, start") fun between(f: String, t: String): Flow<List<Event>>
    @Query("SELECT * FROM events WHERE categoryId = :c ORDER BY date DESC, start DESC") fun byCat(c: Long): Flow<List<Event>>
    @Query("SELECT * FROM events WHERE id = :id") suspend fun byId(id: Long): Event?
    @Query("SELECT COUNT(*) FROM events WHERE date = :d AND id != :ex AND allDay = 0 AND start < :e AND :s < end") suspend fun overlaps(d: String, s: String, e: String, ex: Long): Int
    @Query("SELECT * FROM events WHERE date = :d AND categoryId = 1 AND status != 'Отменён'") suspend fun workOn(d: String): List<Event>
    @Query("SELECT * FROM events WHERE categoryId = 1 AND status = 'Завершено' AND date LIKE :p ORDER BY date") suspend fun monthDone(p: String): List<Event>
    @Query("SELECT * FROM events WHERE clientId = :cid ORDER BY date DESC") suspend fun byClient(cid: Long): List<Event>
    @Query("SELECT * FROM events WHERE repeatType != 'NONE'") fun repeating(): Flow<List<Event>>
    @Query("SELECT * FROM events WHERE repeatType != 'NONE'") suspend fun repeatingList(): List<Event>
    @Insert suspend fun insert(e: Event): Long
    @Update suspend fun update(e: Event)
    @Query("DELETE FROM events WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM events") suspend fun clear()
}
@Dao interface MemberDao {
    @Query("SELECT * FROM family_members ORDER BY id") fun all(): Flow<List<FamilyMember>>
    @Query("SELECT * FROM family_members WHERE id = :id") suspend fun byId(id: Long): FamilyMember?
    @Insert suspend fun insert(m: FamilyMember): Long
    @Update suspend fun update(m: FamilyMember)
    @Query("DELETE FROM family_members") suspend fun clear()
}
@Dao interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name") fun all(): Flow<List<Client>>
    @Query("SELECT * FROM clients WHERE id = :id") suspend fun byId(id: Long): Client?
    @Insert suspend fun insert(c: Client): Long
    @Update suspend fun update(c: Client)
    @Query("DELETE FROM clients WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM clients") suspend fun clear()
}
@Dao interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY name") fun all(): Flow<List<Service>>
    @Query("SELECT * FROM services WHERE id = :id") suspend fun byId(id: Long): Service?
    @Insert suspend fun insert(s: Service): Long
    @Update suspend fun update(s: Service)
    @Query("DELETE FROM services") suspend fun clear()
}
@Dao interface ParticipantDao {
    @Query("SELECT memberId FROM event_participants WHERE eventId = :eid") suspend fun membersOf(eid: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: EventParticipant)
    @Query("DELETE FROM event_participants WHERE eventId = :eid") suspend fun clear(eid: Long)
    @Query("DELETE FROM event_participants") suspend fun clearAll()
}
@Dao interface ShopDao {
    @Query("SELECT * FROM shopping_items ORDER BY bought, id") fun all(): Flow<List<ShoppingItem>>
    @Insert suspend fun insert(s: ShoppingItem): Long
    @Query("UPDATE shopping_items SET bought = :b WHERE id = :id") suspend fun setBought(id: Long, b: Boolean)
    @Query("DELETE FROM shopping_items WHERE bought = 1") suspend fun clearBought()
    @Query("DELETE FROM shopping_items") suspend fun clear()
}
@Dao interface SchoolDao {
    @Query("SELECT * FROM school_schedule") fun all(): Flow<List<SchoolSchedule>>
    @Insert suspend fun insert(s: SchoolSchedule): Long
    @Update suspend fun update(s: SchoolSchedule)
    @Query("DELETE FROM school_schedule") suspend fun clear()
}
@Dao interface WorkScheduleDao {
    @Query("SELECT * FROM work_schedule ORDER BY day") fun all(): Flow<List<WorkSchedule>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(w: WorkSchedule)
}
@Dao interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY done, id DESC") fun all(): Flow<List<Note>>
    @Insert suspend fun insert(n: Note): Long
    @Update suspend fun update(n: Note)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM notes") suspend fun clear()
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun byId(id: Long): Note?
    @Query("SELECT * FROM notes WHERE repeatType != 'NONE'") fun repeating(): Flow<List<Note>>
}
@Dao interface TemplateDao {
    @Query("SELECT * FROM templates") fun all(): Flow<List<Template>>
    @Insert suspend fun insert(t: Template): Long
}
@Dao interface HealthDao {
    @Query("SELECT * FROM health_records ORDER BY date") fun all(): Flow<List<HealthRecord>>
    @Insert suspend fun insert(h: HealthRecord): Long
}
@Dao interface ReminderDao {
    @Query("SELECT * FROM reminder_queue WHERE fired = 0 AND fireAt <= :now") suspend fun due(now: Long): List<ReminderQueue>
    @Query("UPDATE reminder_queue SET fired = 1 WHERE id = :id") suspend fun markFired(id: Long)
    @Insert suspend fun insert(r: ReminderQueue): Long
}
