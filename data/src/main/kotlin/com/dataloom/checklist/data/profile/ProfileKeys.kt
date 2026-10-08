package com.dataloom.checklist.data.profile

import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import com.google.crypto.tink.Aead
import com.google.crypto.tink.Configuration
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeystore
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64

/**
 * Why profile key material cannot be used. Never escapes the data layer: the repository turns it
 * into a [com.dataloom.checklist.domain.model.ProfileState].
 */
sealed class ProfileKeyException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The keys are gone or invalidated for good; data encrypted with them can never be read again. */
    class KeyLost(message: String, cause: Throwable? = null) : ProfileKeyException(message, cause)

    /** The Keystore failed in a way that may be temporary. Nothing should be deleted. */
    class Unavailable(message: String, cause: Throwable? = null) : ProfileKeyException(message, cause)
}

/** Source of the AEAD that encrypts the profile. Methods throw only [ProfileKeyException]. */
interface ProfileAeadProvider {

    /** Stored in `user_profile.key_alias` and bound into the associated data of every ciphertext. */
    val keyAlias: String

    /** The AEAD for existing key material, or null when no profile key was ever created. */
    fun existingAead(): Aead?

    fun getOrCreateAead(): Aead

    /** Deletes all key material (best effort, never throws). The next write creates fresh keys. */
    fun destroyKeys()
}

/** A non-exportable key that wraps the profile keyset. */
interface MasterKey {
    val alias: String
    fun exists(): Boolean
    fun create()
    fun aead(): Aead
    fun delete()
}

/**
 * Android Keystore AES-256-GCM key, generated inside the Keystore (TEE-backed where the device has
 * one) and never exportable. No user authentication is required, so a lock-screen change does not
 * invalidate it; Keystore resets and new devices still lose it, which [KeysetProfileAeadProvider]
 * reports as [ProfileKeyException.KeyLost].
 */
class AndroidKeystoreMasterKey(override val alias: String) : MasterKey {
    override fun exists(): Boolean = AndroidKeystore.hasKey(alias)
    override fun create() = AndroidKeystore.generateNewAes256GcmKey(alias)
    override fun aead(): Aead = AndroidKeystore.getAead(alias)
    override fun delete() = AndroidKeystore.deleteKey(alias)
}

/**
 * Envelope encryption, the pattern Tink recommends on Android: a Tink AES-256-GCM keyset encrypts
 * the profile, and the keyset itself is stored encrypted by the [MasterKey] in a private
 * SharedPreferences file ([PREFS_FILE]) that backup rules exclude. Keystore operations are slow,
 * so the master key is used only to unwrap the keyset once per process.
 *
 * Tests pass a software master key: Robolectric has no Android Keystore.
 */
class KeysetProfileAeadProvider(
    private val prefs: SharedPreferences,
    private val masterKey: MasterKey,
) : ProfileAeadProvider {

    private var cached: Aead? = null

    /** Registry-backed configuration with the AEAD key types registered; the non-deprecated Tink APIs take it explicitly. */
    private val tinkConfig: Configuration = run {
        AeadConfig.register()
        RegistryConfiguration.get()
    }

    override val keyAlias: String get() = masterKey.alias

    @Synchronized
    override fun existingAead(): Aead? {
        cached?.let { return it }
        val stored = prefs.getString(PREF_KEYSET, null) ?: return null
        return guarded { unwrap(stored) }.also { cached = it }
    }

    @Synchronized
    override fun getOrCreateAead(): Aead = existingAead() ?: guarded { create() }.also { cached = it }

    @Synchronized
    override fun destroyKeys() {
        cached = null
        runCatching { prefs.edit().remove(PREF_KEYSET).commit() }
        runCatching { if (masterKey.exists()) masterKey.delete() }
    }

    private fun unwrap(stored: String): Aead {
        val wrapped = try {
            Base64.getDecoder().decode(stored)
        } catch (e: IllegalArgumentException) {
            throw ProfileKeyException.KeyLost("stored keyset is corrupt", e)
        }
        if (!masterKey.exists()) throw ProfileKeyException.KeyLost("master key is missing")
        return TinkProtoKeysetFormat.parseEncryptedKeyset(wrapped, masterKey.aead(), KEYSET_ASSOCIATED_DATA, tinkConfig).aead()
    }

    private fun create(): Aead {
        if (!masterKey.exists()) masterKey.create()
        val keyset = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        val wrapped = TinkProtoKeysetFormat.serializeEncryptedKeyset(keyset, masterKey.aead(), KEYSET_ASSOCIATED_DATA, tinkConfig)
        val written = prefs.edit().putString(PREF_KEYSET, Base64.getEncoder().encodeToString(wrapped)).commit()
        if (!written) throw ProfileKeyException.Unavailable("could not store the keyset")
        return keyset.aead()
    }

    private fun KeysetHandle.aead(): Aead = getPrimitive(tinkConfig, Aead::class.java)

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: ProfileKeyException) {
        throw e
    } catch (e: Exception) {
        throw classifyKeyFailure(e)
    }

    companion object {
        /** File name of the private SharedPreferences holding the wrapped keyset; excluded from backup. */
        const val PREFS_FILE = "checklist_profile_keyset"

        /** Keystore alias of the master key. */
        const val MASTER_KEY_ALIAS = "checklist_profile_master_v1"

        private const val PREF_KEYSET = "profile_keyset"

        /** Binds the wrapped keyset to its purpose, so another wrapped blob cannot be swapped in. */
        private val KEYSET_ASSOCIATED_DATA = "checklist/user_profile/keyset/v1".toByteArray(Charsets.UTF_8)
    }
}

/**
 * Sorts a Keystore or Tink failure into "lost for good" and "maybe temporary". Invalidated or
 * unrecoverable keys and failed authentication (wrong or replaced key) are permanent. Keystore
 * service errors are treated as temporary, so a busy or flaky Keystore never causes data loss.
 */
internal fun classifyKeyFailure(error: Throwable): ProfileKeyException {
    val chain = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
    return when {
        chain.any { it is KeyPermanentlyInvalidatedException || it is UnrecoverableKeyException } ->
            ProfileKeyException.KeyLost("key invalidated", error)
        chain.any { it is ProviderException || it is KeyStoreException } ->
            ProfileKeyException.Unavailable("keystore failure", error)
        chain.any { it is GeneralSecurityException } ->
            ProfileKeyException.KeyLost("key cannot decrypt", error)
        else -> ProfileKeyException.Unavailable("unexpected key failure", error)
    }
}

private const val MAX_CAUSE_DEPTH = 10
