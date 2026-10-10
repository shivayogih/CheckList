package com.dataloom.checklist.data.profile

import android.content.SharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.subtle.AesGcmJce
import java.lang.reflect.Proxy
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Regression test for CL-380: the very first profile save on a phone failed with "Secure storage is
 * not working right now", because the keyset was generated before Tink's AEAD key types were
 * registered.
 *
 * Deliberately a plain JUnit test, not Robolectric: Robolectric loads Tink in its own class loader,
 * and the other profile tests register Tink there (see [softwareAead]). Here Tink is fresh, exactly as
 * in a newly started app, and the master key below wraps keys without touching Tink's registry, as the
 * Android Keystore one does.
 */
class FirstKeyWithoutTinkRegistrationTest {

    private val master = object : MasterKey {
        private var key: Aead? = null
        override val alias = "plain_master"
        override fun exists() = key != null
        override fun create() {
            key = AesGcmJce(ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes))
        }
        override fun aead(): Aead = checkNotNull(key)
        override fun delete() {
            key = null
        }
    }

    @Test
    fun firstKeysetIsCreatedInAFreshProcess() {
        val prefs = inMemoryPrefs()
        val ad = "ad".toByteArray()

        val ciphertext = KeysetProfileAeadProvider(prefs, master).getOrCreateAead().encrypt("secret".toByteArray(), ad)

        val reloaded = KeysetProfileAeadProvider(prefs, master).existingAead()!!
        assertArrayEquals("secret".toByteArray(), reloaded.decrypt(ciphertext, ad))
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

/**
 * Just enough SharedPreferences for the keyset provider, usable without Android: strings only, and an
 * edit is applied at once (commit always succeeds).
 */
private fun inMemoryPrefs(): SharedPreferences {
    val values = mutableMapOf<String, String>()
    val editor = proxy<SharedPreferences.Editor> { self, name, args ->
        when (name) {
            "putString" -> self.also { values[args[0] as String] = args[1] as String }
            "remove" -> self.also { values.remove(args[0] as String) }
            "commit" -> true
            "apply" -> Unit
            else -> self
        }
    }
    return proxy { _, name, args ->
        when (name) {
            "getString" -> values[args[0] as String] ?: args[1]
            "getAll" -> values.toMap()
            "contains" -> args[0] in values
            "edit" -> editor
            else -> null
        }
    }
}

private inline fun <reified T> proxy(crossinline answer: (self: Any, name: String, args: Array<Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
        answer(self, method.name, args ?: emptyArray())
    } as T
