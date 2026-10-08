package com.dataloom.checklist.data.profile

import com.dataloom.checklist.domain.model.UserProfile
import com.google.crypto.tink.Aead
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Encrypts the profile into the `user_profile.enc_payload` column.
 *
 * The whole profile is one ciphertext (section 5.3): nothing in it needs SQL, and one blob does not
 * reveal which fields are filled or how long each is. The associated data binds that ciphertext to
 * its table, column, row, payload version and key alias, so a blob copied into another row or
 * column, or relabelled with another version or key, fails authentication instead of decrypting.
 */
internal object ProfileCipher {

    /** Version of [ProfilePayload]; stored in `user_profile.schema_version`. */
    const val PAYLOAD_VERSION = 1

    private const val TABLE = "user_profile"
    private const val COLUMN = "enc_payload"

    private val json = Json { ignoreUnknownKeys = true }

    fun encrypt(aead: Aead, profile: UserProfile, rowId: String, keyAlias: String): ByteArray {
        val payload = ProfilePayload(profile.displayName, profile.email, profile.phone, profile.address)
        val plaintext = json.encodeToString(ProfilePayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return aead.encrypt(plaintext, associatedData(rowId, PAYLOAD_VERSION, keyAlias))
    }

    /**
     * @throws java.security.GeneralSecurityException when the ciphertext, its row binding or the key
     *   does not match (tampering or a different key)
     * @throws kotlinx.serialization.SerializationException when the authenticated payload is not a profile
     */
    fun decrypt(aead: Aead, ciphertext: ByteArray, rowId: String, payloadVersion: Int, keyAlias: String): UserProfile {
        val plaintext = aead.decrypt(ciphertext, associatedData(rowId, payloadVersion, keyAlias))
        val payload = json.decodeFromString(ProfilePayload.serializer(), plaintext.toString(Charsets.UTF_8))
        return UserProfile(payload.displayName, payload.email, payload.phone, payload.address)
    }

    fun associatedData(rowId: String, payloadVersion: Int, keyAlias: String): ByteArray =
        "checklist.db/$TABLE/$COLUMN/id=$rowId/v=$payloadVersion/key=$keyAlias".toByteArray(Charsets.UTF_8)
}

/**
 * Plain-text shape inside the ciphertext. Short names keep the blob small; never stored unencrypted.
 *
 * Additive evolution (CL-250): [address] was added without a new payload version. Every field has a
 * default, so payloads written before the address existed (or before the name became optional) still
 * decode, and null fields are not written (`encodeDefaults` is off), so a profile without an address
 * encrypts to the same bytes shape as before. Do not rename these keys.
 */
@Serializable
internal data class ProfilePayload(
    @SerialName("n") val displayName: String? = null,
    @SerialName("e") val email: String? = null,
    @SerialName("p") val phone: String? = null,
    @SerialName("a") val address: String? = null,
) {
    override fun toString(): String = "ProfilePayload(***)"
}
