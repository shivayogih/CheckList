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
    private val profile = UserProfile("Asha Rao", "asha@example.com", "9876543210", "12, 4th Cross, Hubballi", "IN")

    private fun encrypt(p: UserProfile = profile) = ProfileCipher.encrypt(aead, p, "me", "alias")

    @Test
    fun roundTripRestoresEveryField() {
        assertEquals(profile, ProfileCipher.decrypt(aead, encrypt(), "me", 1, "alias"))
        val minimal = UserProfile("ಆಶಾ")
        assertEquals(minimal, ProfileCipher.decrypt(aead, encrypt(minimal), "me", 1, "alias"))
    }

    @Test
    fun addressAndAnEmptyNameRoundTrip() {
        val onlyAddress = UserProfile(address = "12, 4th Cross")
        assertEquals(onlyAddress, ProfileCipher.decrypt(aead, encrypt(onlyAddress), "me", 1, "alias"))
    }

    /** A payload written by the app before CL-250: no address key, and it is still decryptable. */
    @Test
    fun payloadWrittenBeforeTheAddressExistedStillDecrypts() {
        val old = """{"n":"Asha Rao","e":"asha@example.com","p":"+91 98765 43210"}"""
        val ciphertext = aead.encrypt(old.toByteArray(Charsets.UTF_8), ProfileCipher.associatedData("me", 1, "alias"))
        assertEquals(
            // CL-380: the combined number is split into its country and national digits.
            UserProfile("Asha Rao", "asha@example.com", "9876543210", null, "IN"),
            ProfileCipher.decrypt(aead, ciphertext, "me", 1, "alias"),
        )
        val nameOnly = aead.encrypt("""{"n":"ಆಶಾ"}""".toByteArray(Charsets.UTF_8), ProfileCipher.associatedData("me", 1, "alias"))
        assertEquals(UserProfile("ಆಶಾ"), ProfileCipher.decrypt(aead, nameOnly, "me", 1, "alias"))
    }

    /** A newer payload with keys this version does not know is still readable (forward compatibility). */
    @Test
    fun unknownFutureKeysAreIgnored() {
        val future = """{"n":"Asha","a":"Hubballi","z":"later"}"""
        val ciphertext = aead.encrypt(future.toByteArray(Charsets.UTF_8), ProfileCipher.associatedData("me", 1, "alias"))
        assertEquals(UserProfile("Asha", address = "Hubballi"), ProfileCipher.decrypt(aead, ciphertext, "me", 1, "alias"))
    }

    @Test
    fun ciphertextContainsNoPlainText() {
        val bytes = String(encrypt(), Charsets.ISO_8859_1)
        listOf("Asha", "asha@example.com", "98765", "Hubballi").forEach { assertFalse(it, bytes.contains(it)) }
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
