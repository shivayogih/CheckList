package com.dataloom.checklist.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A one-time notification for a list at [triggerAt] (epoch milliseconds). Removed once it has fired. */
@Serializable
data class Reminder(val id: String, val checklistId: String, val triggerAt: Long)

/**
 * Reminders (CL-350). They are alarm settings of this phone, not list content, so they live in the
 * settings DataStore like the other preferences: no database change, and they are not part of an
 * export. A copied list therefore never carries its original's reminders.
 */
interface ReminderStore {
    /** All reminders, soonest first. */
    val reminders: Flow<List<Reminder>>

    suspend fun get(id: String): Reminder? = reminders.first().firstOrNull { it.id == id }

    /** Adds [reminder], or replaces the one with the same ID. */
    suspend fun upsert(reminder: Reminder)

    /** Removes the reminders with these IDs; unknown IDs are ignored. */
    suspend fun delete(ids: Set<String>)
}

@Singleton
class DataStoreReminderStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReminderStore {

    override val reminders: Flow<List<Reminder>> = dataStore.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map { prefs -> decode(prefs[KEY]) }
        .distinctUntilChanged()

    override suspend fun upsert(reminder: Reminder) {
        dataStore.edit { prefs ->
            val others = decode(prefs[KEY]).filterNot { it.id == reminder.id }
            prefs[KEY] = encode(others + reminder)
        }
    }

    override suspend fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs -> prefs[KEY] = encode(decode(prefs[KEY]).filterNot { it.id in ids }) }
    }

    private fun encode(list: List<Reminder>): String = json.encodeToString(list.sortedBy { it.triggerAt })

    /** An unreadable value reads as no reminders rather than breaking the app. */
    private fun decode(raw: String?): List<Reminder> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<Reminder>>(raw).sortedBy { it.triggerAt }
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            emptyList()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("reminders_v1")
        val json = Json { ignoreUnknownKeys = true }
    }
}
