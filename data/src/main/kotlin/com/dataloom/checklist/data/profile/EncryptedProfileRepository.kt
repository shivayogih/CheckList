package com.dataloom.checklist.data.profile

import com.dataloom.checklist.data.local.dao.ProfileDao
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.entity.UserProfileEntity
import com.dataloom.checklist.domain.common.AppLog
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IoDispatcher
import com.dataloom.checklist.domain.model.ProfileState
import com.dataloom.checklist.domain.model.UserProfile
import com.dataloom.checklist.domain.repository.ProfileRepository
import com.google.crypto.tink.Aead
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Stores the profile in the existing `user_profile` row (schema v1, no migration), encrypted with
 * [ProfileCipher] under keys from [ProfileAeadProvider].
 *
 * Failure policy (never crash, never lose data by accident):
 * - Keys lost or invalidated, or a ciphertext that fails authentication (tampering, or a row restored
 *   onto a device without its keys): the row can never be read again, so it is erased, the keys are
 *   destroyed when they are the problem, and observers see [ProfileState.Reset] until the user saves
 *   a new profile or acknowledges the notice.
 * - Keystore failing in a way that may be temporary, or a row written by a newer app version:
 *   nothing is deleted; observers see [ProfileState.Unavailable] and saves return false.
 *
 * Must be a singleton: the reset notice and the write lock live in memory. The notice is not
 * persisted, so if the process dies before the UI shows it, the user simply finds no profile.
 */
class EncryptedProfileRepository @Inject constructor(
    db: CheckListDatabase,
    private val keys: ProfileAeadProvider,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ProfileRepository {

    private val dao: ProfileDao = db.profileDao()
    private val writeLock = Mutex()
    private val resetPending = MutableStateFlow(false)

    /**
     * [resetPending] is combined only as a trigger; its current value is read at evaluation time.
     * combine() delivers each upstream's values from its own collector, so the flag value it hands
     * over can lag behind the real one. [erase] sets the flag while combine is still evaluating, and
     * the row deletion that follows can be combined with the old `false`. That emitted a spurious
     * NotSet, followed by Reset once the stale `true` arrived (CL-155).
     */
    override fun observeProfile(): Flow<ProfileState> =
        combine(dao.observe(ROW_ID), resetPending) { row, _ -> stateOf(row, resetPending.value) }
            .filterNotNull()
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun saveProfile(profile: UserProfile): Boolean = withContext(io) {
        writeLock.withLock {
            val aead = writableAead() ?: return@withLock false
            val payload = try {
                ProfileCipher.encrypt(aead, profile, ROW_ID, keys.keyAlias)
            } catch (e: GeneralSecurityException) {
                AppLog.e(TAG, e) { "Profile encryption failed" }
                return@withLock false
            }
            dao.upsert(UserProfileEntity(ROW_ID, payload, keys.keyAlias, ProfileCipher.PAYLOAD_VERSION, clock.nowMillis()))
            resetPending.value = false
            true
        }
    }

    override suspend fun clearProfile() = withContext(io) {
        writeLock.withLock {
            dao.deleteAll()
            // Crypto-shredding: even if SQLite pages or a backup still hold the old ciphertext,
            // nothing can decrypt it once the keys are gone.
            keys.destroyKeys()
            resetPending.value = false
        }
    }

    override suspend fun acknowledgeReset() {
        resetPending.value = false
    }

    /** Null means "skip this emission": the row changed while it was being erased. */
    private suspend fun stateOf(row: UserProfileEntity?, reset: Boolean): ProfileState? {
        if (row == null) return if (reset) ProfileState.Reset else ProfileState.NotSet
        return when (val read = read(row)) {
            is Read.Ok -> ProfileState.Available(read.profile)
            Read.Unavailable -> ProfileState.Unavailable
            is Read.Lost -> if (erase(row, read.destroyKeys)) ProfileState.Reset else null
        }
    }

    private fun read(row: UserProfileEntity): Read {
        if (row.schemaVersion != ProfileCipher.PAYLOAD_VERSION) return Read.Unavailable
        val aead = try {
            // No keys but a row: the row came back from a backup or device transfer without its
            // keys (they are excluded on purpose), so it was encrypted under keys that no longer exist.
            keys.existingAead() ?: return Read.Lost(destroyKeys = false)
        } catch (e: ProfileKeyException.KeyLost) {
            return Read.Lost(destroyKeys = true)
        } catch (e: ProfileKeyException.Unavailable) {
            return Read.Unavailable
        }
        return try {
            Read.Ok(ProfileCipher.decrypt(aead, row.encPayload, row.id, row.schemaVersion, row.keyAlias))
        } catch (e: GeneralSecurityException) {
            Read.Lost(destroyKeys = false)
        } catch (e: SerializationException) {
            Read.Lost(destroyKeys = false)
        } catch (e: IllegalArgumentException) {
            Read.Lost(destroyKeys = false)
        }
    }

    /** Returns false when a newer profile replaced [row] meanwhile; that one is left alone. */
    private suspend fun erase(row: UserProfileEntity, destroyKeys: Boolean): Boolean = writeLock.withLock {
        if (dao.deleteIfUnchanged(row.id, row.encPayload) == 0) return@withLock false
        if (destroyKeys) keys.destroyKeys()
        resetPending.value = true
        true
    }

    /**
     * Keys for a write. Lost keys are replaced (the old row is unreadable anyway). A Keystore that
     * fails in a maybe-temporary way gets one retry; if it still fails and there is no stored profile
     * that a reset could destroy, the unusable key material is reset and created again, so a broken
     * keyset never blocks the user from saving (CL-380). The cause is logged (debug builds only).
     */
    private suspend fun writableAead(): Aead? = try {
        keys.getOrCreateAead()
    } catch (e: ProfileKeyException.KeyLost) {
        AppLog.w(TAG, e) { "Profile keys lost; resetting them" }
        recreateKeys()
    } catch (e: ProfileKeyException.Unavailable) {
        AppLog.w(TAG, e) { "Profile keys unavailable; retrying once" }
        try {
            keys.getOrCreateAead()
        } catch (retry: ProfileKeyException) {
            if (dao.get(ROW_ID) == null) {
                AppLog.w(TAG, retry) { "Profile keys still unavailable and no profile is stored; resetting them" }
                recreateKeys()
            } else {
                AppLog.e(TAG, retry) { "Profile keys unavailable; the stored profile is kept" }
                null
            }
        }
    }

    private suspend fun recreateKeys(): Aead? {
        dao.deleteAll()
        keys.destroyKeys()
        return try {
            keys.getOrCreateAead()
        } catch (e: ProfileKeyException) {
            AppLog.e(TAG, e) { "Profile keys cannot be created" }
            null
        }
    }

    private sealed interface Read {
        data class Ok(val profile: UserProfile) : Read
        data object Unavailable : Read
        data class Lost(val destroyKeys: Boolean) : Read
    }

    companion object {
        /** The profile is a single row (section 5.3). */
        const val ROW_ID = "me"

        private const val TAG = "ProfileStore"
    }
}
