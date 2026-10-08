package com.dataloom.checklist.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** Raw seed JSON. An interface so tests can feed catalogs without packaging assets. */
interface SeedSource {

    /** Contents of catalog.json, or null when the app ships no catalog. */
    fun catalog(): String?

    /** Contents of every i18n/<locale>.json. */
    fun translations(): List<String>
}

/** Reads the catalog bundled in assets/seed. */
class AssetSeedSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SeedSource {

    override fun catalog(): String? = try {
        read("$ROOT/catalog.json")
    } catch (missing: FileNotFoundException) {
        null
    }

    override fun translations(): List<String> =
        context.assets.list(I18N).orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
            .map { read("$I18N/$it") }

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        const val ROOT = "seed"
        const val I18N = "$ROOT/i18n"
    }
}
