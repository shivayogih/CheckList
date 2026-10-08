package com.dataloom.checklist.data.profile

import com.dataloom.checklist.domain.model.UserProfile
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileCipherTest {

    private val aead = softwareAead()
    private val profile = UserProfile("Asha Rao", "asha@example.com", "+91 98765 43210")

    private fun encrypt(p: UserProfile = profile) = ProfileCipher.encrypt(aead, p, "me", "alias")

    @Test
    fun roundTripRestoresEveryField() {
        assertEquals(profile, ProfileCipher.decrypt(aead, encrypt(), "me", 1, "alias"))
        val minimal = UserProfile("ಆಶಾ")
        assertEquals(minimal, ProfileCipher.decrypt(aead, encrypt(minimal), "me", 1, "alias"))
    }

    @Test
    fun ciphertextContainsNoPlainText() {
        val bytes = String(encrypt(), Charsets.ISO_8859_1)
        listOf("Asha", "asha@example.com", "98765").forEach { assertFalse(it, bytes.contains(it)) }
    }

    @Test
    fun sameProfileEncryptsDifferentlyEachTime() {
        assertFalse(encrypt().contentEquals(encrypt()))
    }

    @Test
    fun anyFlippedByteIsDetected() {
        val ciphertext = encrypt()
        for (i in ciphertext.indices) {
            val tampered = ciphertext.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertThrows(GeneralSecurityException::class.java) { ProfileCipher.decrypt(aead, tampered, "me", 1, "alias") }
        }
    }

    @Test
    fun truncatedOrExtendedCiphertextIsDetected() {
        val ciphertext = encrypt()
        assertThrows(GeneralSecurityException::class.java) {
            ProfileCipher.decrypt(aead, ciphertext.copyOf(ciphertext.size - 1), "me", 1, "alias")
        }
        assertThrows(GeneralSecurityException::class.java) {
            ProfileCipher.decrypt(aead, ciphertext + 0.toByte(), "me", 1, "alias")
        }
    }

    @Test
    fun ciphertextIsBoundToRowVersionAndKeyAlias() {
        val ciphertext = encrypt()
        assertThrows(GeneralSecurityException::class.java) { ProfileCipher.decrypt(aead, ciphertext, "other", 1, "alias") }
        assertThrows(GeneralSecurityException::class.java) { ProfileCipher.decrypt(aead, ciphertext, "me", 2, "alias") }
        assertThrows(GeneralSecurityException::class.java) { ProfileCipher.decrypt(aead, ciphertext, "me", 1, "other") }
    }

    @Test
    fun associatedDataNamesTableAndColumn() {
        val ad = String(ProfileCipher.associatedData("me", 1, "alias"), Charsets.UTF_8)
        assertEquals("checklist.db/user_profile/enc_payload/id=me/v=1/key=alias", ad)
        assertNotEquals(ad, String(ProfileCipher.associatedData("me2", 1, "alias"), Charsets.UTF_8))
    }

    @Test
    fun anotherKeyCannotDecrypt() {
        assertThrows(GeneralSecurityException::class.java) {
            ProfileCipher.decrypt(softwareAead(), encrypt(), "me", 1, "alias")
        }
    }
}
