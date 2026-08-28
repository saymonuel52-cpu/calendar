package com.family.daily

import com.family.daily.data.AppDb
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object Backup {
    suspend fun export(db: AppDb): String {
        val root = JSONObject()
        root.put("events", JSONArray().also { a -> db.events().between("0000-01-01", "9999-12-31").first().forEach { e ->
            a.put(JSONObject().put("id", e.id).put("title", e.title).put("date", e.date).put("start", e.start).put("end", e.end).put("allDay", e.allDay).put("categoryId", e.categoryId).put("note", e.note).put("reminders", e.reminders).put("status", e.status).put("clientId", e.clientId ?: 0).put("serviceId", e.serviceId ?: 0).put("price", e.price ?: 0).put("sourceType", e.sourceType).put("silent", e.silent))
        } })
        root.put("clients", JSONArray().also { a -> db.clients().all().first().forEach { c -> a.put(JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone).put("source", c.source).put("note", c.note)) } })
        root.put("services", JSONArray().also { a -> db.services().all().first().forEach { s -> a.put(JSONObject().put("id", s.id).put("name", s.name).put("duration", s.duration).put("price", s.price)) } })
        root.put("members", JSONArray().also { a -> db.members().all().first().forEach { m -> a.put(JSONObject().put("id", m.id).put("name", m.name).put("role", m.role).put("phone", m.phone).put("birthYear", m.birthYear ?: 0)) } })
        root.put("notes", JSONArray().also { a -> db.notes().all().first().forEach { n -> a.put(JSONObject().put("id", n.id).put("text", n.text).put("date", n.date).put("time", n.time).put("reminder", n.reminder ?: -1).put("done", n.done)) } })
        root.put("shop", JSONArray().also { a -> db.shop().all().first().forEach { s -> a.put(JSONObject().put("id", s.id).put("title", s.title).put("bought", s.bought)) } })
        root.put("school", JSONArray().also { a -> db.school().all().first().forEach { s -> a.put(JSONObject().put("id", s.id).put("childId", s.childId).put("start", s.start).put("end", s.end).put("enabled", s.enabled)) } })
        return root.toString()
    }

    suspend fun import(db: AppDb, json: String) {
        val root = JSONObject(json)
        db.events().clear(); db.participants().clearAll(); db.clients().clear(); db.services().clear(); db.members().clear(); db.notes().clear(); db.shop().clear(); db.school().clear()
        val ev = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until ev.length()) { val o = ev.getJSONObject(i)
            db.events().insert(com.family.daily.data.Event(id = o.optLong("id"), title = o.optString("title"), date = o.optString("date"), start = o.optString("start"), end = o.optString("end"), allDay = o.optBoolean("allDay"), categoryId = o.optLong("categoryId", 1), note = o.optString("note"), reminders = o.optString("reminders"), status = o.optString("status"), clientId = o.optLong("clientId", 0).takeIf { it != 0L }, serviceId = o.optLong("serviceId", 0).takeIf { it != 0L }, price = o.optDouble("price", 0.0).takeIf { it != 0.0 }, sourceType = o.optString("sourceType", "EVENT"), silent = o.optBoolean("silent")))
        }
        val cl = root.optJSONArray("clients") ?: JSONArray()
        for (i in 0 until cl.length()) { val o = cl.getJSONObject(i); db.clients().insert(com.family.daily.data.Client(id = o.optLong("id"), name = o.optString("name"), phone = o.optString("phone"), source = o.optString("source"), note = o.optString("note"))) }
        val sv = root.optJSONArray("services") ?: JSONArray()
        for (i in 0 until sv.length()) { val o = sv.getJSONObject(i); db.services().insert(com.family.daily.data.Service(id = o.optLong("id"), name = o.optString("name"), duration = o.optInt("duration", 60), price = o.optDouble("price", 0.0))) }
        val mm = root.optJSONArray("members") ?: JSONArray()
        for (i in 0 until mm.length()) { val o = mm.getJSONObject(i); db.members().insert(com.family.daily.data.FamilyMember(id = o.optLong("id"), name = o.optString("name"), role = o.optString("role", "parent"), phone = o.optString("phone"), birthYear = o.optInt("birthYear", 0).takeIf { it != 0 })) }
        val nt = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until nt.length()) { val o = nt.getJSONObject(i); db.notes().insert(com.family.daily.data.Note(id = o.optLong("id"), text = o.optString("text"), date = o.optString("date"), time = o.optString("time"), reminder = o.optInt("reminder", -1).takeIf { it >= 0 }, done = o.optBoolean("done"))) }
        val sh = root.optJSONArray("shop") ?: JSONArray()
        for (i in 0 until sh.length()) { val o = sh.getJSONObject(i); db.shop().insert(com.family.daily.data.ShoppingItem(id = o.optLong("id"), title = o.optString("title"), bought = o.optBoolean("bought"))) }
        val sc = root.optJSONArray("school") ?: JSONArray()
        for (i in 0 until sc.length()) { val o = sc.getJSONObject(i); db.school().insert(com.family.daily.data.SchoolSchedule(id = o.optLong("id"), childId = o.optLong("childId"), start = o.optString("start", "08:00"), end = o.optString("end", "13:00"), enabled = o.optBoolean("enabled", true))) }
    }
}
