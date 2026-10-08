package com.dataloom.checklist.data.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.security.ProviderException

/** A software AES-256-GCM AEAD standing in for Keystore-backed keys (Robolectric has no Keystore). */
fun softwareAead(): Aead {
    AeadConfig.register()
    return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}

/**
 * Test double of the Keystore master key. [lose] simulates a Keystore reset or invalidation,
 * [replace] a different key under the same alias, [failing] a temporarily broken Keystore.
 */
class FakeMasterKey(override val alias: String = "test_master") : MasterKey {

    private var key: Aead? = null
    var failing = false
    var creations = 0
        private set

    override fun exists(): Boolean {
        check()
        return key != null
    }

    override fun create() {
        check()
        key = softwareAead()
        creations++
    }

    override fun aead(): Aead {
        check()
        return key ?: throw java.security.GeneralSecurityException("no key")
    }

    override fun delete() {
        check()
        key = null
    }

    fun lose() {
        key = null
    }

    fun replace() {
        key = softwareAead()
    }

    private fun check() {
        if (failing) throw ProviderException("Keystore temporarily unavailable")
    }
}

fun testPrefs(name: String = KeysetProfileAeadProvider.PREFS_FILE): SharedPreferences =
    ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
