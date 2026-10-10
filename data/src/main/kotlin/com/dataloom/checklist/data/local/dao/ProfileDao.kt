package com.dataloom.checklist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dataloom.checklist.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/** The single encrypted profile row. Only `EncryptedProfileRepository` uses it; it never sees plain text. */
@Dao
interface ProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = :id")
    fun observe(id: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :id")
    suspend fun get(id: String): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    /**
     * Deletes the row only if it still holds [payload]. Every encryption uses a fresh random nonce,
     * so a profile saved in the meantime never matches and is not erased by mistake.
     */
    @Query("DELETE FROM user_profile WHERE id = :id AND enc_payload = :payload")
    suspend fun deleteIfUnchanged(id: String, payload: ByteArray): Int

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll(): Int
}
