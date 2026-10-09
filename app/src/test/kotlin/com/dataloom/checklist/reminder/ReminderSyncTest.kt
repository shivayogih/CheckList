package com.dataloom.checklist.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator
import com.dataloom.checklist.testing.FakeCatalogRepository
import com.dataloom.checklist.testing.FakeChecklistRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Reminders and the lists stay in step: saving, clean-up on delete and archive, and firing (CL-350). */
@RunWith(RobolectricTestRunner::class)
class ReminderSyncTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo = FakeChecklistRepository(FakeCatalogRepository())
    private val scheduler = RecordingScheduler()
    private val store = DataStoreReminderStore(
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") },
    )
    private var nextId = 0
    private val sync = ReminderSync(
        store = store,
        scheduler = scheduler,
        checklists = repo,
        clock = Clock { NOW },
        ids = IdGenerator { "reminder-${nextId++}" },
        context = app,
        scope = scope,
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a time in the past is refused and nothing is saved`() = runBlocking {
        val list = repo.createChecklist("Trip", null, emptyList())

        assertFalse(sync.save(list.value, NOW))

        assertTrue(store.reminders.first().isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `saving sets the alarm, and changing a reminder keeps its ID`() = runBlocking {
        val list = repo.createChecklist("Trip", null, emptyList())

        assertTrue(sync.save(list.value, NOW + HOUR))
        assertTrue(sync.save(list.value, NOW + 2 * HOUR, existingId = "reminder-0"))

        assertEquals(listOf(Reminder("reminder-0", list.value, NOW + 2 * HOUR)), store.reminders.first())
        assertEquals(NOW + 2 * HOUR, scheduler.scheduled["reminder-0"])
    }

    @Test
    fun `deleting or archiving a list removes its reminders and their alarms`() = runBlocking {
        val deleted = repo.createChecklist("Deleted", null, emptyList())
        val archived = repo.createChecklist("Archived", null, emptyList())
        val kept = repo.createChecklist("Kept", null, emptyList())
        sync.save(deleted.value, NOW + HOUR)
        sync.save(archived.value, NOW + HOUR)
        sync.save(kept.value, NOW + HOUR)
        sync.start()

        repo.deleteChecklist(deleted)
        repo.setArchived(archived, archived = true)

        awaitUntil { store.reminders.first().map { it.checklistId } == listOf(kept.value) }
        assertEquals(setOf("reminder-0", "reminder-1"), scheduler.cancelled)
    }

    @Test
    fun `a fired reminder shows a notification for its list and is removed`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val list = repo.createChecklist("Weekly shopping", null, emptyList())
        sync.save(list.value, NOW + HOUR)

        sync.fire("reminder-0")

        assertTrue(store.reminders.first().isEmpty())
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, posted.size)
        assertTrue(posted.single().extras.getString(Notification.EXTRA_TITLE).orEmpty().contains("Weekly shopping"))
    }

    @Test
    fun `a reminder whose list was archived meanwhile shows nothing`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val list = repo.createChecklist("Old trip", null, emptyList())
        sync.save(list.value, NOW + HOUR)
        repo.setArchived(list, archived = true)

        sync.fire("reminder-0")

        assertTrue(store.reminders.first().isEmpty())
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }

    private suspend fun awaitUntil(condition: suspend () -> Boolean) = withTimeout(TIMEOUT_MILLIS) {
        while (!condition()) delay(POLL_MILLIS)
    }

    private class RecordingScheduler : ReminderScheduler {
        val scheduled = mutableMapOf<String, Long>()
        val cancelled = mutableSetOf<String>()

        override fun schedule(reminder: Reminder) {
            scheduled[reminder.id] = reminder.triggerAt
        }

        override fun cancel(reminderId: String) {
            scheduled.remove(reminderId)
            cancelled += reminderId
        }
    }

    private companion object {
        const val NOW = 1_000_000L
        const val HOUR = 3_600_000L
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
