package com.dataloom.checklist.domain.transfer

/** A small valid file in the shape of the format document's example. */
object TransferFixtures {

    val metadata = TransferMetadata(
        app = "CheckList",
        appVersion = "1.0.0",
        exportedAt = "2026-10-08T04:00:00Z",
        locale = "kn",
        includesProfile = false,
    )

    fun document(
        units: List<TransferUnit> = listOf(TransferUnit("u1", "CUSTOM", "Bundle", allowsDecimal = false)),
        categories: List<TransferCategory> = listOf(
            TransferCategory("c1", canonicalKey = "groceries"),
            TransferCategory("c2", customName = "Pooja Items", icon = "🪔"),
        ),
        checklists: List<TransferChecklist> = listOf(
            TransferChecklist(
                ref = "k1",
                title = "Diwali Shopping",
                description = "For the festival",
                createdAt = "2026-10-01T10:00:00Z",
                sections = listOf(TransferSection("s1", "c1", 0), TransferSection("s2", "c2", 1)),
            ),
        ),
        items: List<TransferItem> = listOf(
            TransferItem("i1", "s1", canonicalKey = "rice", displayName = "ಅಕ್ಕಿ", displayNameLocale = "kn", quantity = "5", unit = "KG"),
            TransferItem("i2", "s1", displayName = "Oil", displayNameLocale = "en", quantity = "1.5", unit = "LITRE", completed = true, position = 1),
            TransferItem("i3", "s2", displayName = "Flowers", displayNameLocale = "en", quantity = "2", unit = "u1", notes = "Marigold"),
        ),
        formatVersion: Int = TransferFormat.JSON_FORMAT_VERSION,
        schemaVersion: Int = TransferFormat.SCHEMA_VERSION,
    ) = TransferDocument(formatVersion, schemaVersion, metadata, units, categories, checklists, items)

    fun checklist(ref: String, title: String, vararg sections: TransferSection) =
        TransferChecklist(ref = ref, title = title, sections = sections.toList())

    fun item(ref: String, section: String, name: String = "Item $ref", quantity: String? = null, unit: String? = null) =
        TransferItem(ref, section, displayName = name, displayNameLocale = "en", quantity = quantity, unit = unit)
}
