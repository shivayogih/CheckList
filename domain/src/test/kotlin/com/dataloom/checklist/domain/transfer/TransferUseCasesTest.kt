package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.fake.BytesSource
import com.dataloom.checklist.domain.fake.DirectTransactionRunner
import com.dataloom.checklist.domain.fake.EndlessSource
import com.dataloom.checklist.domain.fake.FailingSource
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.fake.FakeChecklistRepository
import com.dataloom.checklist.domain.fake.FakeTransferCodec
import com.dataloom.checklist.domain.fake.MemorySink
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.transfer.TransferFixtures.checklist
import com.dataloom.checklist.domain.transfer.TransferFixtures.document
import com.dataloom.checklist.domain.transfer.TransferFixtures.item
import com.dataloom.checklist.domain.validation.ValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferUseCasesTest {

    /** One "device": its repositories and the three use cases. */
    private class Device(val codec: FakeTransferCodec) {
        val catalog = FakeCatalogRepository()
        val checklists = FakeChecklistRepository(catalog)
        val transactions = DirectTransactionRunner()
        val groceries: Category = catalog.seedCategory("groceries", "Groceries")
        val vegetables: Category = catalog.seedCategory("vegetables", "Vegetables")
        val export = ExportChecklistsUseCase(
            checklists, catalog, codec, Clock { 1_791_432_000_000L }, Dispatchers.Unconfined, Dispatchers.Unconfined,
        )
        val preview = PreviewImportUseCase(checklists, catalog, codec, Dispatchers.Unconfined, Dispatchers.Unconfined)
        val apply = ApplyImportUseCase(checklists, catalog, transactions)

        suspend fun previewOf(document: TransferDocument): ImportPreviewResult = preview(BytesSource(codec.bytesFor(document)), "en")

        suspend fun importOf(document: TransferDocument): ImportResult {
            val ready = previewOf(document) as ImportPreviewResult.Ready
            return apply(ready.preview.validated, "en")
        }
    }

    private val codec = FakeTransferCodec()
    private val device = Device(codec)

    private suspend fun Device.sampleChecklist(): ChecklistId {
        val bundle = catalog.createCustomUnit("Bundle", allowsDecimal = false)
        val pooja = catalog.createCategory("Pooja Items", "🪔")
        val id = checklists.createChecklist("Diwali Shopping", "For the festival", listOf(groceries.id, pooja))
        val sections = checklists.detail(id)!!.sections
        val ids = checklists.addItems(
            sections[0].id,
            listOf(
                NewChecklistItem(null, "rice", "ಅಕ್ಕಿ", "kn", Quantity.of(5), UnitCode("KG"), null),
                NewChecklistItem(null, null, "Oil", "en", Quantity.parse("1.5"), UnitCode("LITRE"), "Sunflower"),
            ),
        )
        checklists.setItemCompleted(ids[1], true)
        checklists.addItems(sections[1].id, listOf(NewChecklistItem(null, null, "Flowers", "en", Quantity.of(2), bundle, null)))
        return id
    }

    /** Comparable content of a checklist: everything except IDs and timestamps. */
    private fun Device.content(detail: ChecklistDetail): List<Any?> = listOf(
        detail.checklist.title,
        detail.checklist.description,
        detail.checklist.isArchived,
        detail.sections.map { section ->
            section.category.displayName to section.items.map {
                listOf(
                    it.canonicalKey, it.displayName, it.displayNameLocale, it.quantity, it.notes, it.isCompleted,
                    it.unit?.let { code -> if (code.isCustom) catalog.allUnitsLabel(code) else code.value },
                )
            }
        },
    )

    private fun FakeCatalogRepository.allUnitsLabel(code: UnitCode): String? = runBlocking { getUnit(code)?.customLabel }

    @Test
    fun `export writes file-local refs and never database ids`() = runTest {
        val id = device.sampleChecklist()
        val doc = device.export.buildDocument(
            listOf(device.checklists.detail(id)!!),
            ExportRequest(locale = "kn", appVersion = "1.0.0"),
        )
        assertEquals(listOf("k1"), doc.checklists.map { it.ref })
        assertEquals(listOf("s1", "s2"), doc.checklists.single().sections.map { it.ref })
        assertEquals(listOf(TransferCategory("c1", canonicalKey = "groceries"), TransferCategory("c2", customName = "Pooja Items", icon = "🪔")), doc.categories)
        assertEquals(listOf(TransferUnit("u1", "CUSTOM", "Bundle", allowsDecimal = false)), doc.units)
        assertEquals(listOf("KG", "LITRE", "u1"), doc.items.map { it.unit })
        assertEquals(listOf("5", "1.5", "2"), doc.items.map { it.quantity })
        assertEquals(listOf(0, 1, 0), doc.items.map { it.position })
        assertEquals("2026-10-08T04:00:00Z", doc.metadata.exportedAt)
        assertEquals("1970-01-01T00:00:01Z", doc.checklists.single().createdAt)
        assertFalse(doc.metadata.includesProfile)
        assertFalse(doc.toString().contains("cl-") || doc.toString().contains("sec-") || doc.toString().contains("item-"))
    }

    @Test
    fun `round trip export then import recreates the same content`() = runTest {
        val source = device.sampleChecklist()
        device.checklists.setArchived(source, true)
        val sink = MemorySink()
        val exported = device.export(ExportRequest(locale = "en", appVersion = "1.0.0"), sink) as ExportResult.Exported
        assertEquals(1, exported.checklistCount)
        assertEquals(3, exported.itemCount)

        val other = Device(codec)
        val preview = (other.preview(BytesSource(sink.out.toByteArray()), "en") as ImportPreviewResult.Ready).preview
        assertEquals(1, preview.checklistCount)
        assertEquals(3, preview.itemCount)
        assertEquals(1, preview.completedItemCount)
        assertEquals(listOf("Pooja Items"), preview.newCategories)
        assertEquals(1, preview.matchedCategoryCount)
        assertEquals(listOf("Bundle"), preview.newUnits)
        assertEquals(emptyList<ChecklistRename>(), preview.renamedChecklists)

        val summary = (other.apply(preview.validated, "en") as ImportResult.Imported).summary
        assertEquals(1, other.transactions.transactions)
        val imported = other.checklists.detail(summary.checklistIds.single())!!
        assertEquals(device.content(device.checklists.detail(source)!!), other.content(imported))
    }

    @Test
    fun `duplicate titles are imported under numbered titles`() = runTest {
        device.checklists.createChecklist("Goa Trip", null, emptyList())
        val doc = document(
            checklists = listOf(checklist("k1", "Goa Trip"), checklist("k2", "goa trip"), checklist("k3", "Packing")),
            items = emptyList(),
        )
        val preview = (device.previewOf(doc) as ImportPreviewResult.Ready).preview
        assertEquals(
            listOf(ChecklistRename("Goa Trip", "Goa Trip (2)"), ChecklistRename("goa trip", "goa trip (3)")),
            preview.renamedChecklists,
        )
        val summary = (device.apply(preview.validated, "en") as ImportResult.Imported).summary
        assertEquals(listOf("Goa Trip (2)", "goa trip (3)", "Packing"), summary.checklistIds.map { device.checklists.checklist(it)!!.title })
        assertEquals(4, device.checklists.checklistCount())
    }

    @Test
    fun `numbered titles stay within the title limit`() {
        val long = "ಅ".repeat(100)
        val numbered = ImportPlanner.numberedTitle(long, 12)
        assertEquals(100, numbered.codePointCount(0, numbered.length))
        assertTrue(numbered.endsWith(" (12)"))
    }

    @Test
    fun `categories and units are mapped to existing ones and never duplicated`() = runTest {
        val gifts = device.catalog.createCategory("Gifts", "🎁")
        device.catalog.createCustomUnit("bundle", allowsDecimal = false)
        val doc = document(
            units = listOf(
                TransferUnit("u1", "CUSTOM", "Bundle", allowsDecimal = false),
                TransferUnit("u2", "CUSTOM", "Bunch", allowsDecimal = false),
                TransferUnit("u3", "CUSTOM", "BUNCH", allowsDecimal = false),
                TransferUnit("u4", "CUSTOM", "Unused", allowsDecimal = false),
            ),
            categories = listOf(
                TransferCategory("c1", canonicalKey = "groceries"),
                TransferCategory("c2", customName = "GIFTS"),
                TransferCategory("c3", customName = "Pooja Items"),
                TransferCategory("c4", customName = "pooja items"),
                TransferCategory("c5", canonicalKey = "festival_lights"),
                TransferCategory("c6", customName = "Never used"),
            ),
            checklists = listOf(
                checklist("k1", "One", TransferSection("s1", "c1", 0), TransferSection("s2", "c2", 1), TransferSection("s3", "c3", 2)),
                checklist("k2", "Two", TransferSection("s4", "c4", 0), TransferSection("s5", "c5", 1), TransferSection("s6", "c1", 2)),
            ),
            items = listOf(
                item("i1", "s1", quantity = "1", unit = "u1"),
                item("i2", "s3", quantity = "1", unit = "u2"),
                item("i3", "s4", quantity = "1", unit = "u3"),
            ),
        )
        val before = device.catalog.allCategories().size
        val preview = (device.previewOf(doc) as ImportPreviewResult.Ready).preview
        assertEquals(listOf("Pooja Items", "Festival lights"), preview.newCategories)
        assertEquals(2, preview.matchedCategoryCount)
        assertEquals(listOf("Bunch"), preview.newUnits)
        assertEquals(1, preview.matchedUnitCount)

        device.apply(preview.validated, "en") as ImportResult.Imported
        assertEquals(before + 2, device.catalog.allCategories().size)
        val labels = device.catalog.observeUnits().first().mapNotNull { it.customLabel }
        assertEquals(listOf("bundle", "Bunch"), labels)
        val one = device.checklists.observeChecklists(ChecklistQuery()).first()
        assertEquals(2, one.size)
        val sectionsOfTwo = device.checklists.detail(one.single { it.checklist.title == "Two" }.checklist.id)!!.sections
        assertEquals(listOf("Pooja Items", "Festival lights", "Groceries"), sectionsOfTwo.map { it.category.displayName })
        val sectionsOfOne = device.checklists.detail(one.single { it.checklist.title == "One" }.checklist.id)!!.sections
        assertEquals(gifts, sectionsOfOne[1].category.id)
    }

    @Test
    fun `two refs resolving to one category in a checklist are refused`() = runTest {
        val doc = document(
            categories = listOf(TransferCategory("c1", canonicalKey = "groceries"), TransferCategory("c2", customName = "groceries")),
            checklists = listOf(checklist("k1", "One", TransferSection("s1", "c1", 0), TransferSection("s2", "c2", 1))),
            items = emptyList(),
        )
        val rejection = (device.previewOf(doc) as ImportPreviewResult.Rejected).rejection as ImportRejection.Invalid
        assertEquals(ImportProblem.DUPLICATE_CATEGORY_IN_CHECKLIST, rejection.issues.single().problem)
    }

    @Test
    fun `apply re-matches against data created after the preview`() = runTest {
        val preview = (device.previewOf(document()) as ImportPreviewResult.Ready).preview
        assertEquals(listOf("Pooja Items"), preview.newCategories)
        device.catalog.createCategory("Pooja items", "🪔")
        val summary = (device.apply(preview.validated, "en") as ImportResult.Imported).summary
        assertEquals(0, summary.newCategoryCount)
        assertEquals(1, device.catalog.allCategories().count { it.displayName.equals("pooja items", ignoreCase = true) })
    }

    @Test
    fun `apply writes nothing when the data changed in a way that breaks the file`() = runTest {
        val doc = document(
            units = listOf(TransferUnit("u1", "CUSTOM", "Bundle", allowsDecimal = true)),
            items = listOf(item("i1", "s2", quantity = "1.5", unit = "u1")),
        )
        val preview = (device.previewOf(doc) as ImportPreviewResult.Ready).preview
        device.catalog.createCustomUnit("bundle", allowsDecimal = false)
        val result = device.apply(preview.validated, "en") as ImportResult.Rejected
        val issue = (result.rejection as ImportRejection.Invalid).issues.single()
        assertEquals(listOf(ValidationError.QUANTITY_MUST_BE_WHOLE), issue.fieldErrors)
        assertEquals(0, device.checklists.checklistCount())
        assertEquals(0, device.transactions.transactions)
    }

    @Test
    fun `completion and archive state are kept and items follow their positions`() = runTest {
        val doc = document(
            checklists = listOf(checklist("k1", "Trip", TransferSection("s2", "c2", 5), TransferSection("s1", "c1", 1)).copy(archived = true)),
            items = listOf(
                item("i1", "s1", name = "Second").copy(position = 9),
                item("i2", "s1", name = "First").copy(position = 2, completed = true),
            ),
        )
        val summary = (device.importOf(doc) as ImportResult.Imported).summary
        val detail = device.checklists.detail(summary.checklistIds.single())!!
        assertTrue(detail.checklist.isArchived)
        assertEquals(listOf("Groceries", "Pooja Items"), detail.sections.map { it.category.displayName })
        assertEquals(listOf("First" to true, "Second" to false), detail.sections[0].items.map { it.displayName to it.isCompleted })
    }

    @Test
    fun `oversize files are refused by reported size and by actual length`() = runTest {
        val tooBig = BytesSource(ByteArray(1), sizeBytes = TransferLimits.MAX_FILE_BYTES + 1)
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.FileTooLarge), device.preview(tooBig, "en"))
        assertFalse(tooBig.opened)
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.FileTooLarge), device.preview(EndlessSource(sizeBytes = 10), "en"))
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.FileTooLarge), device.preview(EndlessSource(), "en"))
    }

    @Test
    fun `unreadable and malformed files are refused`() = runTest {
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.Unreadable), device.preview(FailingSource(), "en"))
        codec.forcedResult = DecodeResult.Rejected(ImportRejection.Malformed)
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.Malformed), device.preview(BytesSource(ByteArray(3)), "en"))
    }

    @Test
    fun `a newer file is refused with an update hint`() = runTest {
        val result = device.previewOf(document(schemaVersion = 2))
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.UnsupportedVersion(1, 2, requiresNewerApp = true)), result)
        assertEquals(0, device.checklists.checklistCount())
    }

    @Test
    fun `a file with a profile is imported without it`() = runTest {
        val preview = (device.previewOf(document().copy(profilePresent = true)) as ImportPreviewResult.Ready).preview
        assertTrue(preview.profileSkipped)
    }

    @Test
    fun `export refuses selections the importer could not read back`() = runTest {
        val sink = MemorySink()
        assertEquals(ExportResult.NothingToExport, device.export(ExportRequest(locale = "en", appVersion = "1"), sink))
        device.checklists.createChecklist("One", null, emptyList())
        codec.encodedSize = (TransferLimits.MAX_FILE_BYTES + 1).toInt()
        assertEquals(
            ExportResult.TooLarge(TransferLimit.FILE_SIZE, TransferLimits.MAX_FILE_BYTES),
            device.export(ExportRequest(locale = "en", appVersion = "1"), sink),
        )
        assertFalse(sink.opened)
    }

    @Test
    fun `export of a selection skips deleted checklists`() = runTest {
        val kept = device.checklists.createChecklist("Kept", null, emptyList())
        val sink = MemorySink()
        val result = device.export(ExportRequest(listOf(kept, ChecklistId("gone"), kept), locale = "en", appVersion = "1"), sink)
        assertEquals(ExportResult.Exported(1, 0, sink.out.size()), result)
    }
}
