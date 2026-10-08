package com.dataloom.checklist.data.importexport

import com.dataloom.checklist.domain.photo.NoPhotoStore
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.data.FakeClock
import com.dataloom.checklist.data.FakeSeedSource
import com.dataloom.checklist.data.SequentialIds
import com.dataloom.checklist.data.inMemoryDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.repository.RoomCatalogRepository
import com.dataloom.checklist.data.repository.RoomChecklistRepository
import com.dataloom.checklist.data.seed.AssetSeedSource
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.data.seed.SeedSource
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.ChecklistFilter
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistQuery
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.transfer.ApplyImportUseCase
import com.dataloom.checklist.domain.transfer.ChecklistRename
import com.dataloom.checklist.domain.transfer.DecodeResult
import com.dataloom.checklist.domain.transfer.ExportChecklistsUseCase
import com.dataloom.checklist.domain.transfer.ExportRequest
import com.dataloom.checklist.domain.transfer.ExportResult
import com.dataloom.checklist.domain.transfer.ExportSink
import com.dataloom.checklist.domain.transfer.ImportPreview
import com.dataloom.checklist.domain.transfer.ImportPreviewResult
import com.dataloom.checklist.domain.transfer.ImportProblem
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.ImportResult
import com.dataloom.checklist.domain.transfer.ImportSource
import com.dataloom.checklist.domain.transfer.PreviewImportUseCase
import com.dataloom.checklist.domain.transfer.TransferDocument
import com.dataloom.checklist.domain.transfer.TransferLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Export and import end to end on Room: real repositories, real codec, real transactions. */
@RunWith(RobolectricTestRunner::class)
class RoomImportExportTest {

    /** One phone: its own database seeded with the bundled catalog (or a test one). */
    private class Device(prefix: String, seed: SeedSource = AssetSeedSource(ApplicationProvider.getApplicationContext())) {
        val clock = FakeClock(now = 1_791_432_000_000L)
        val ids = SequentialIds(prefix)
        val db: CheckListDatabase = inMemoryDatabase()
        val checklists = RoomChecklistRepository(db, clock, ids, NoPhotoStore)
        val catalog = RoomCatalogRepository(db, clock, ids)
        val codec = JsonTransferCodec()
        val export = ExportChecklistsUseCase(checklists, catalog, codec, clock)
        val preview = PreviewImportUseCase(checklists, catalog, codec)
        val apply = ApplyImportUseCase(checklists, catalog, RoomTransactionRunner(db))

        init {
            SeedLoader(seed, clock, SequentialIds("$prefix-seed")).seedIfNeeded(db.openHelper.writableDatabase)
        }

        fun count(table: String): Int =
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

        suspend fun category(key: String): Category =
            catalog.observeCategories("en", includeHidden = true).first().single { it.canonicalKey == key }

        suspend fun exportAll(locale: String = "en"): ByteArray {
            val sink = MemorySink()
            assertTrue(export(ExportRequest(locale = locale, appVersion = "1.0.0"), sink) is ExportResult.Exported)
            return sink.bytes()
        }

        suspend fun previewOf(bytes: ByteArray, locale: String = "en"): ImportPreviewResult = preview(BytesSource(bytes), locale)

        suspend fun importOf(bytes: ByteArray, locale: String = "en"): ImportResult {
            val ready = previewOf(bytes, locale) as ImportPreviewResult.Ready
            return apply(ready.preview.validated, locale)
        }

        suspend fun detail(id: ChecklistId, locale: String = "en") = checklists.observeChecklist(id, locale).first()!!

        suspend fun allIds(): List<ChecklistId> =
            checklists.observeChecklists(ChecklistQuery(filter = ChecklistFilter.ALL)).first().map { it.checklist.id }
    }

    private class MemorySink : ExportSink {
        private val out = ByteArrayOutputStream()
        override fun openStream() = out
        fun bytes(): ByteArray = out.toByteArray()
    }

    private class BytesSource(private val bytes: ByteArray, override val sizeBytes: Long? = bytes.size.toLong()) : ImportSource {
        override fun openStream(): InputStream = ByteArrayInputStream(bytes)
    }

    private val phoneA = Device("a")
    private val phoneB = Device("b")

    @After
    fun tearDown() {
        phoneA.db.close()
        phoneB.db.close()
    }

    /** Builds two checklists in every script, with seeded and custom categories and a custom unit. */
    private suspend fun Device.sampleData(): List<ChecklistId> {
        val bundle = catalog.createCustomUnit("Bundle", allowsDecimal = false)
        val pooja = catalog.createCategory("Pooja Samagri", "🪔")
        val diwali = checklists.createChecklist("Diwali Shopping", "For the festival", listOf(category("groceries").id, pooja))
        val sections = detail(diwali).sections.map { it.id }
        val added = checklists.addItems(
            sections[0],
            listOf(
                item("ಅಕ್ಕಿ", "kn", "5", "KG", key = "rice"),
                item("चावल का आटा", "hi", "2.5", "KG"),
                item("அரிசி மாவு", "ta", "500", "GRAM", notes = "இரண்டு பாக்கெட்\nசிறியது"),
                item("బియ్యం", "te", null, null),
            ),
        )
        checklists.setItemCompleted(added[1], true)
        checklists.addItems(sections[1], listOf(item("अगरबत्ती", "mr", "3", bundle.value), item("കർപ്പൂരം", "ml", "1", "PACK")))

        val trip = checklists.createChecklist("Goa Trip", null, listOf(category("travel").id))
        checklists.addItems(detail(trip).sections.single().id, listOf(item("Sunscreen", "en", "2", "BOTTLE")))
        checklists.setArchived(trip, true)
        return listOf(diwali, trip)
    }

    private fun item(name: String, locale: String, quantity: String?, unit: String?, key: String? = null, notes: String? = null) =
        NewChecklistItem(null, key, name, locale, quantity?.let { Quantity.parse(it) }, unit?.let(::UnitCode), notes)

    /**
     * Content of a file without refs, timestamps or anything device-specific, so two exports of the
     * same data compare equal.
     */
    private fun content(bytes: ByteArray): List<Any?> {
        val doc = (phoneA.codec.decode(bytes) as DecodeResult.Decoded).document
        return content(doc)
    }

    private fun content(doc: TransferDocument): List<Any?> {
        val categories = doc.categories.associateBy { it.ref }
        val units = doc.units.associateBy { it.ref }
        return doc.checklists.sortedBy { it.title }.map { checklist ->
            listOf(
                checklist.title,
                checklist.description,
                checklist.archived,
                checklist.sections.sortedBy { it.order }.map { section ->
                    val category = categories.getValue(section.categoryRef).let { listOf(it.canonicalKey, it.customName, it.icon) }
                    category to doc.items.filter { it.sectionRef == section.ref }.sortedBy { it.position }.map {
                        listOf(
                            it.canonicalKey, it.displayName, it.displayNameLocale, it.quantity, it.notes, it.completed,
                            units[it.unit]?.let { unit -> unit.label to unit.allowsDecimal } ?: it.unit,
                        )
                    }
                },
            )
        }
    }

    @Test
    fun exportThenImportOnAnotherPhoneGivesTheSameContent() = runTest {
        phoneA.sampleData()
        val exported = phoneA.exportAll(locale = "kn")

        val preview = (phoneB.previewOf(exported) as ImportPreviewResult.Ready).preview
        assertEquals(2, preview.checklistCount)
        assertEquals(7, preview.itemCount)
        assertEquals(1, preview.completedItemCount)
        assertEquals(listOf("Pooja Samagri"), preview.newCategories)
        assertEquals(listOf("Bundle"), preview.newUnits)
        assertEquals("kn", preview.sourceLocale)
        assertFalse(preview.profileSkipped)

        val summary = (phoneB.apply(preview.validated, "en") as ImportResult.Imported).summary
        assertEquals(2, summary.checklistIds.size)
        assertEquals(content(exported), content(phoneB.exportAll()))

        // Seeded categories travel by key, so the Kannada export reads in Hindi on the other phone.
        val details = summary.checklistIds.map { phoneB.detail(it, locale = "hi") }
        val diwali = details.single { it.checklist.title == "Diwali Shopping" }
        assertEquals(phoneB.category("groceries").id, diwali.sections.first().category.id)
        assertEquals("किराना", diwali.sections.first().category.displayName)
        assertTrue(details.single { it.checklist.title == "Goa Trip" }.checklist.isArchived)
    }

    @Test
    fun exportedFilesNeverContainDatabaseIds() = runTest {
        val ids = phoneA.sampleData()
        val text = phoneA.exportAll().decodeToString()
        ids.forEach { assertFalse(text.contains(it.value)) }
        assertFalse(text.contains("a-"))
        assertTrue(text.contains("\"profile\": null"))
    }

    @Test
    fun reimportingOnTheSamePhoneRenamesTitlesAndReusesCategoriesAndUnits() = runTest {
        phoneA.sampleData()
        val exported = phoneA.exportAll()
        val categoriesBefore = phoneA.count("category")
        val unitsBefore = phoneA.count("unit_def")

        val preview = (phoneA.previewOf(exported) as ImportPreviewResult.Ready).preview
        assertEquals(emptyList<String>(), preview.newCategories)
        assertEquals(emptyList<String>(), preview.newUnits)
        assertEquals(
            listOf(ChecklistRename("Diwali Shopping", "Diwali Shopping (2)"), ChecklistRename("Goa Trip", "Goa Trip (2)")),
            preview.renamedChecklists.sortedBy { it.originalTitle },
        )
        phoneA.apply(preview.validated, "en") as ImportResult.Imported
        phoneA.importOf(exported) as ImportResult.Imported

        assertEquals(categoriesBefore, phoneA.count("category"))
        assertEquals(unitsBefore, phoneA.count("unit_def"))
        val titles = phoneA.allIds().map { phoneA.detail(it).checklist.title }.sorted()
        assertEquals(
            listOf("Diwali Shopping", "Diwali Shopping (2)", "Diwali Shopping (3)", "Goa Trip", "Goa Trip (2)", "Goa Trip (3)"),
            titles,
        )
    }

    @Test
    fun aFailureHalfwayThroughRollsBackEveryWrite() = runTest {
        phoneA.sampleData()
        val exported = phoneA.exportAll()
        val tables = listOf("checklist", "checklist_category", "checklist_item", "category", "unit_def", "item_search_fts")
        val before = tables.associateWith { phoneB.count(it) }

        // Fails on the second checklist's items, after units, categories and a whole checklist were written.
        val failing = FailingChecklistRepository(phoneB.checklists, failOnAddItemsCall = 3)
        val apply = ApplyImportUseCase(failing, phoneB.catalog, RoomTransactionRunner(phoneB.db))
        val preview = (phoneB.previewOf(exported) as ImportPreviewResult.Ready).preview
        try {
            apply(preview.validated, "en")
            fail("expected the injected failure")
        } catch (expected: IllegalStateException) {
            assertEquals("injected", expected.message)
        }

        assertEquals(before, tables.associateWith { phoneB.count(it) })
        assertTrue(phoneB.allIds().isEmpty())
    }

    @Test
    fun goldenFileImportsWithEveryScriptIntact() = runTest {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/transfer/valid-full.json")).use { it.readBytes() }
        val summary = (phoneB.importOf(bytes) as ImportResult.Imported).summary
        val detail = phoneB.detail(summary.checklistIds.single())
        assertEquals(
            listOf("ಅಕ್ಕಿ", "चावल का आटा", "பூ மாலை", "अगरबत्ती"),
            detail.sections.flatMap { s -> s.items.map { it.displayName } },
        )
        assertEquals("ಮಧ್ಯಮ ಗಾತ್ರ\nచిన్న ప్యాకెట్", detail.sections[0].items[1].notes)
        // "Pooja Items" is a seeded category name, so the custom name maps to it instead of a copy.
        assertEquals(phoneB.category("pooja_items").id, detail.sections[1].category.id)
        assertEquals(1, summary.newUnitCount)
        assertEquals(0, summary.newCategoryCount)
    }

    @Test
    fun hostileStringsAreStoredAsPlainTextWithControlsRemoved() = runTest {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/transfer/hostile-strings.json")).use { it.readBytes() }
        val preview = (phoneB.previewOf(bytes) as ImportPreviewResult.Ready).preview
        assertTrue(preview.profileSkipped)
        val summary = (phoneB.apply(preview.validated, "en") as ImportResult.Imported).summary
        val detail = phoneB.detail(summary.checklistIds.single())
        assertEquals("Trip '; DELETE FROM checklist; --", detail.checklist.title)
        assertEquals("Line 1\nLine 2", detail.checklist.description)
        assertEquals("Robert'); DROP TABLE category;--", detail.sections.single().category.displayName)
        val item = detail.sections.single().items.single()
        assertEquals("gnp.exe ಕ್ಷ‍ತ್ರ 👨‍👩‍👧", item.displayName)
        assertEquals("%' OR '1'='1", item.notes)
        assertEquals(Quantity.parse("1.25"), item.quantity)
        // The tables are all still there, and search with LIKE wildcards in the title still works.
        assertEquals(1, phoneB.checklists.observeChecklists(ChecklistQuery(search = "DELETE")).first().size)
    }

    @Test
    fun oversizeAndFutureFilesWriteNothing() = runTest {
        val huge = BytesSource(ByteArray(16), sizeBytes = TransferLimits.MAX_FILE_BYTES + 1)
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.FileTooLarge), phoneB.preview(huge, "en"))
        val padded = ByteArray((TransferLimits.MAX_FILE_BYTES + 1).toInt()) { ' '.code.toByte() }
        assertEquals(ImportPreviewResult.Rejected(ImportRejection.FileTooLarge), phoneB.preview(BytesSource(padded, sizeBytes = null), "en"))
        val future = requireNotNull(javaClass.getResourceAsStream("/transfer/future-version.json")).use { it.readBytes() }
        assertEquals(
            ImportPreviewResult.Rejected(ImportRejection.UnsupportedVersion(3, 3, requiresNewerApp = true)),
            phoneB.previewOf(future),
        )
        assertEquals(0, phoneB.count("checklist"))
    }

    @Test
    fun aBuiltInUnitMissingFromThisDatabaseIsReportedNotDropped() = runTest {
        val small = Device("c", seed = FakeSeedSource())
        try {
            phoneA.sampleData()
            val rejection = (small.previewOf(phoneA.exportAll()) as ImportPreviewResult.Rejected).rejection as ImportRejection.Invalid
            assertTrue(rejection.issues.all { it.problem == ImportProblem.UNKNOWN_UNIT })
            assertEquals(3, rejection.totalIssues)
        } finally {
            small.db.close()
        }
    }

    @Test
    fun selectedChecklistsOnlyAreExported() = runTest {
        val (diwali, _) = phoneA.sampleData()
        val sink = MemorySink()
        val result = phoneA.export(ExportRequest(listOf(diwali), locale = "en", appVersion = "1.0.0"), sink)
        assertEquals(6, (result as ExportResult.Exported).itemCount)
        val preview: ImportPreview = (phoneB.previewOf(sink.bytes()) as ImportPreviewResult.Ready).preview
        assertEquals(1, preview.checklistCount)
    }

    /** Delegates to Room but throws on the n-th addItems call, to prove the import rolls back. */
    private class FailingChecklistRepository(
        private val delegate: ChecklistRepository,
        private val failOnAddItemsCall: Int,
    ) : ChecklistRepository by delegate {
        private var calls = 0

        override suspend fun addItems(sectionId: SectionId, items: List<NewChecklistItem>) =
            if (++calls == failOnAddItemsCall) throw IllegalStateException("injected") else delegate.addItems(sectionId, items)
    }
}
