package com.dataloom.checklist.data.profile

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeysetProfileAeadProviderTest {

    private val prefs = testPrefs()
    private val master = FakeMasterKey()
    private val provider = KeysetProfileAeadProvider(prefs, master)
    private val ad = "ad".toByteArray()

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun noKeysExistUntilTheFirstWrite() {
        assertNull(provider.existingAead())
        assertEquals(0, master.creations)
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun createdKeysetIsStoredWrappedAndReloadsInANewProcess() {
        val ciphertext = provider.getOrCreateAead().encrypt("secret".toByteArray(), ad)
        assertEquals(1, master.creations)

        // A new provider over the same storage is what the next app start sees.
        val reloaded = KeysetProfileAeadProvider(prefs, master).existingAead()

        assertNotNull(reloaded)
        assertArrayEquals("secret".toByteArray(), reloaded!!.decrypt(ciphertext, ad))
        assertEquals(1, master.creations)
    }

    @Test
    fun storedKeysetIsUselessWithoutTheMasterKey() {
        provider.getOrCreateAead()
        master.lose()

        assertThrows(ProfileKeyException.KeyLost::class.java) { KeysetProfileAeadProvider(prefs, master).existingAead() }
    }

    @Test
    fun aDifferentMasterKeyCannotUnwrapTheKeyset() {
        provider.getOrCreateAead()
        master.replace()

        assertThrows(ProfileKeyException.KeyLost::class.java) { KeysetProfileAeadProvider(prefs, master).existingAead() }
    }

    @Test
    fun corruptStoredKeysetIsReportedAsLost() {
        prefs.edit().putString("profile_keyset", "%%% not base64 %%%").commit()
        master.create()

        assertThrows(ProfileKeyException.KeyLost::class.java) { provider.existingAead() }
    }

    @Test
    fun temporaryKeystoreFailureIsUnavailableNotLost() {
        provider.getOrCreateAead()
        master.failing = true

        assertThrows(ProfileKeyException.Unavailable::class.java) { KeysetProfileAeadProvider(prefs, master).existingAead() }
        assertTrue(prefs.contains("profile_keyset"))
    }

    @Test
    fun destroyKeysRemovesKeysetAndMasterKeyAndNeverThrows() {
        provider.getOrCreateAead()

        provider.destroyKeys()

        assertFalse(prefs.contains("profile_keyset"))
        assertFalse(master.exists())
        assertNull(provider.existingAead())

        master.failing = true
        provider.destroyKeys()
    }

    @Test
    fun freshKeysAfterDestroyCannotReadOldCiphertext() {
        val old = provider.getOrCreateAead().encrypt("secret".toByteArray(), ad)
        provider.destroyKeys()

        val fresh = provider.getOrCreateAead()

        assertThrows(GeneralSecurityException::class.java) { fresh.decrypt(old, ad) }
    }

    @Test
    fun failureClassification() {
        assertTrue(classifyKeyFailure(KeyPermanentlyInvalidatedException()) is ProfileKeyException.KeyLost)
        assertTrue(
            classifyKeyFailure(GeneralSecurityException("wrap", KeyPermanentlyInvalidatedException())) is ProfileKeyException.KeyLost,
        )
        assertTrue(classifyKeyFailure(GeneralSecurityException("decryption failed")) is ProfileKeyException.KeyLost)
        assertTrue(classifyKeyFailure(ProviderException("busy")) is ProfileKeyException.Unavailable)
        assertTrue(classifyKeyFailure(GeneralSecurityException("wrap", ProviderException())) is ProfileKeyException.Unavailable)
        assertTrue(classifyKeyFailure(KeyStoreException("io")) is ProfileKeyException.Unavailable)
        assertTrue(classifyKeyFailure(IllegalStateException()) is ProfileKeyException.Unavailable)
    }
}
