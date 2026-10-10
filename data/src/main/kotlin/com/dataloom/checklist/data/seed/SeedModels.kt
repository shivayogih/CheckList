package com.dataloom.checklist.data.seed

import kotlinx.serialization.Serializable

// Format of assets/seed/catalog.json and assets/seed/i18n/<locale>.json (section 6.4).
// Unknown fields are ignored so the asset format can grow without breaking older loaders.

@Serializable
data class SeedCatalog(
    /** Bump whenever the asset changes; the loader re-applies only a higher version. */
    val seedVersion: Int,
    val units: List<SeedUnit> = emptyList(),
    val categories: List<SeedCategory> = emptyList(),
    val items: List<SeedItem> = emptyList(),
    /**
     * Keys of seed categories that were merged into another one. They are hidden once they hold no
     * items (user items included), never deleted.
     */
    val retiredCategories: List<String> = emptyList(),
)

@Serializable
data class SeedUnit(
    val code: String,
    val allowsDecimal: Boolean,
    val sortOrder: Int = 0,
)

/**
 * [sortOrder] is part of the asset contract but schema v1 has no column for it: categories are
 * ordered by usage and then by name in the user's language (section 7).
 */
@Serializable
data class SeedCategory(
    val key: String,
    val icon: String,
    val sortOrder: Int = 0,
    /** Id in the source master catalogue (CAT001..), informational. */
    val catalogId: String? = null,
)

@Serializable
data class SeedItem(
    val key: String,
    /** Canonical key of the category. */
    val category: String,
    val defaultUnit: String? = null,
    /** Id in the source master catalogue (ITM0001..), informational. */
    val catalogId: String? = null,
    /** Grouping inside the category. Stored as English search text, not as a column. */
    val subcategory: String? = null,
    /** Tag ids of the source catalogue. Stored as English search text, not as a column. */
    val tags: List<String> = emptyList(),
)

@Serializable
data class SeedTranslations(
    val locale: String,
    /** Category canonical key -> name. */
    val categories: Map<String, String> = emptyMap(),
    /** Item canonical key -> name and aliases. */
    val items: Map<String, SeedItemName> = emptyMap(),
)

@Serializable
data class SeedItemName(
    val name: String,
    val aliases: List<String> = emptyList(),
)
