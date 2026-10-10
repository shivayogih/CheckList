package com.dataloom.checklist.data.profile

import app.cash.turbine.test
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.local.entity.UserProfileEntity
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedProfileRepositoryTest {

    private val db = inMemoryDatabase()
    private val dao = db.profileDao()
    private val clock = FakeClock(now = 42_000L)
    private val prefs = testPrefs()
    private val master = FakeMasterKey()
    private var repository = newRepository()

    private val asha = UserProfile("Asha Rao", "asha@example.com", "9876543210", phoneCountry = "IN")
    private val ravi = UserProfile("Ravi")
    private val withAddress = UserProfile("Kamala", null, "9845012345", "12, 4th Cross, Vidyanagar, Hubballi", "IN")

    /** A new provider has no cached keyset, like the next app start. */
    private fun newRepository() =
        EncryptedProfileRepository(db, KeysetProfileAeadProvider(prefs, master), clock, Dispatchers.Unconfined)

    private fun restartApp() {
        repository = newRepository()
    }

    @After
    fun tearDown() {
        db.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun addressIsStoredEncryptedAndRoundTrips() = runTest {
        assertTrue(repository.saveProfile(withAddress))

        assertEquals(ProfileState.Available(withAddress), repository.observeProfile().first())
        val stored = String(dao.get(EncryptedProfileRepository.ROW_ID)!!.encPayload, Charsets.ISO_8859_1)
        assertFalse(stored.contains("Hubballi"))
    }

    /**
     * CL-250: a row saved by an older app version has no address key and must still open. CL-380: its
     * phone held the whole "+91 ..." number, which now reads back as country and national digits.
     */
    @Test
    fun profileStoredBeforeTheAddressExistedStillDecrypts() = runTest {
        repository.saveProfile(ravi) // creates the keys
        val row = dao.get(EncryptedProfileRepository.ROW_ID)!!
        val aead = KeysetProfileAeadProvider(prefs, master).existingAead()!!
        val oldPayload = """{"n":"Asha Rao","e":"asha@example.com","p":"+91 98765 43210"}"""
        val oldRow = aead.encrypt(
            oldPayload.toByteArray(Charsets.UTF_8),
            ProfileCipher.associatedData(row.id, row.schemaVersion, row.keyAlias),
        )
        dao.upsert(UserProfileEntity(row.id, oldRow, row.keyAlias, row.schemaVersion, row.updatedAt))
        restartApp()

        assertEquals(ProfileState.Available(asha), repository.observeProfile().first())
        // Saving again upgrades the row in place and keeps working.
        assertTrue(repository.saveProfile(withAddress))
        assertEquals(ProfileState.Available(withAddress), repository.observeProfile().first())
    }

    @Test
    fun startsWithNoProfileAndCreatesNoKeysJustByReading() = runTest {
        assertEquals(ProfileState.NotSet, repository.observeProfile().first())
        assertEquals(0, master.creations)
    }

    @Test
    fun savedProfileIsStoredEncryptedAndObserved() = runTest {
        assertTrue(repository.saveProfile(asha))

        assertEquals(ProfileState.Available(asha), repository.observeProfile().first())
        val row = dao.get(EncryptedProfileRepository.ROW_ID)!!
        val stored = String(row.encPayload, Charsets.ISO_8859_1)
        listOf("Asha", "asha@example.com", "98765").forEach { assertFalse(it, stored.contains(it)) }
        assertEquals(master.alias, row.keyAlias)
        assertEquals(ProfileCipher.PAYLOAD_VERSION, row.schemaVersion)
        assertEquals(42_000L, row.updatedAt)
    }

    @Test
    fun profileSurvivesAnAppRestart() = runTest {
        repository.saveProfile(asha)

        restartApp()

        assertEquals(ProfileState.Available(asha), repository.observeProfile().first())
    }

    @Test
    fun saveReplacesThePreviousProfile() = runTest {
        repository.observeProfile().test {
            assertEquals(ProfileState.NotSet, awaitItem())
            repository.saveProfile(asha)
            assertEquals(ProfileState.Available(asha), awaitItem())
            repository.saveProfile(ravi)
            assertEquals(ProfileState.Available(ravi), awaitItem())
        }
    }

    @Test
    fun clearDeletesTheRowAndDestroysTheKeys() = runTest {
        repository.saveProfile(asha)

        repository.clearProfile()

        assertEquals(ProfileState.NotSet, repository.observeProfile().first())
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
        assertFalse(prefs.contains("profile_keyset"))
        assertFalse(master.exists())
    }

    // Key loss and tampering

    @Test
    fun lostKeystoreKeyErasesTheProfileAndReportsReset() = runTest {
        repository.saveProfile(asha)
        master.lose()
        restartApp()

        assertEquals(ProfileState.Reset, repository.observeProfile().first())
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
        assertFalse(prefs.contains("profile_keyset"))
    }

    @Test
    fun replacedKeystoreKeyIsTreatedAsLost() = runTest {
        repository.saveProfile(asha)
        master.replace()
        restartApp()

        assertEquals(ProfileState.Reset, repository.observeProfile().first())
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
    }

    @Test
    fun resetStaysUntilAcknowledgedOrANewProfileIsSaved() = runTest {
        repository.saveProfile(asha)
        master.lose()
        restartApp()

        repository.observeProfile().test {
            assertEquals(ProfileState.Reset, awaitItem())
            repository.acknowledgeReset()
            assertEquals(ProfileState.NotSet, awaitItem())
            repository.saveProfile(ravi)
            assertEquals(ProfileState.Available(ravi), awaitItem())
        }
        assertEquals(2, master.creations)
    }

    @Test
    fun saveAfterKeyLossStartsOverWithFreshKeys() = runTest {
        repository.saveProfile(asha)
        master.lose()
        restartApp()

        assertTrue(repository.saveProfile(ravi))

        assertEquals(ProfileState.Available(ravi), repository.observeProfile().first())
        assertEquals(2, master.creations)
    }

    @Test
    fun tamperedCiphertextIsDetectedErasedAndReported() = runTest {
        repository.saveProfile(asha)

        repository.observeProfile().test {
            assertEquals(ProfileState.Available(asha), awaitItem())
            val row = dao.get(EncryptedProfileRepository.ROW_ID)!!
            val tampered = row.encPayload.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
            dao.upsert(UserProfileEntity(row.id, tampered, row.keyAlias, row.schemaVersion, row.updatedAt))

            assertEquals(ProfileState.Reset, awaitItem())
        }
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
        // The keys were fine; only the data was bad.
        assertTrue(prefs.contains("profile_keyset"))
    }

    @Test
    fun relabelledRowFailsAuthentication() = runTest {
        repository.saveProfile(asha)
        val row = dao.get(EncryptedProfileRepository.ROW_ID)!!
        dao.upsert(UserProfileEntity(row.id, row.encPayload, "another_alias", row.schemaVersion, row.updatedAt))

        assertEquals(ProfileState.Reset, repository.observeProfile().first())
    }

    @Test
    fun rowRestoredFromBackupWithoutKeysIsErased() = runTest {
        // Encrypted on "another phone": the keyset and Keystore key never came along.
        val otherPhone = ProfileCipher.encrypt(softwareAead(), asha, EncryptedProfileRepository.ROW_ID, master.alias)
        dao.upsert(UserProfileEntity(EncryptedProfileRepository.ROW_ID, otherPhone, master.alias, ProfileCipher.PAYLOAD_VERSION, 1L))

        assertEquals(ProfileState.Reset, repository.observeProfile().first())
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
        assertEquals(0, master.creations)
    }

    // Nothing is deleted when the failure may be temporary

    @Test
    fun temporaryKeystoreFailureKeepsTheProfile() = runTest {
        repository.saveProfile(asha)
        master.failing = true
        restartApp()

        assertEquals(ProfileState.Unavailable, repository.observeProfile().first())
        assertFalse(repository.saveProfile(ravi))
        assertNotNull(dao.get(EncryptedProfileRepository.ROW_ID))

        master.failing = false
        restartApp()
        assertEquals(ProfileState.Available(asha), repository.observeProfile().first())
    }

    @Test
    fun clearAlwaysWorksEvenWhenTheKeystoreFails() = runTest {
        repository.saveProfile(asha)
        master.failing = true
        restartApp()

        repository.clearProfile()

        assertEquals(ProfileState.NotSet, repository.observeProfile().first())
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
    }

    @Test
    fun rowFromANewerAppVersionIsKept() = runTest {
        repository.saveProfile(asha)
        val row = dao.get(EncryptedProfileRepository.ROW_ID)!!
        dao.upsert(UserProfileEntity(row.id, row.encPayload, row.keyAlias, ProfileCipher.PAYLOAD_VERSION + 1, row.updatedAt))

        assertEquals(ProfileState.Unavailable, repository.observeProfile().first())
        assertNotNull(dao.get(EncryptedProfileRepository.ROW_ID))
    }

    // A Keystore problem never blocks the first save (CL-380)

    @Test
    fun firstSaveSurvivesAOneOffKeystoreError() = runTest {
        master.transientFailures = 1

        assertTrue(repository.saveProfile(asha))
        assertEquals(ProfileState.Available(asha), repository.observeProfile().first())
    }

    @Test
    fun unusableKeysWithNoStoredProfileAreResetSoTheSaveWorks() = runTest {
        KeysetProfileAeadProvider(prefs, master).getOrCreateAead()
        master.brokenUntilDeleted = true
        restartApp()

        assertTrue(repository.saveProfile(ravi))
        assertEquals(2, master.creations)
        restartApp()
        assertEquals(ProfileState.Available(ravi), repository.observeProfile().first())
    }

    @Test
    fun keystoreThatNeverWorksStillReportsTheFailure() = runTest {
        master.failing = true

        assertFalse(repository.saveProfile(asha))
        assertNull(dao.get(EncryptedProfileRepository.ROW_ID))
    }
}
