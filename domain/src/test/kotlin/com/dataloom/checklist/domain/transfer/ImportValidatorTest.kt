package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.transfer.TransferFixtures.checklist
import com.dataloom.checklist.domain.transfer.TransferFixtures.document
import com.dataloom.checklist.domain.transfer.TransferFixtures.item
import com.dataloom.checklist.domain.validation.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportValidatorTest {

    private fun issues(document: TransferDocument): List<ImportIssue> =
        (ImportValidator.validate(document) as ImportRejection.Invalid).issues

    private fun problems(document: TransferDocument): List<ImportProblem> = issues(document).map { it.problem }

    @Test
    fun `the example file is valid`() {
        assertNull(ImportValidator.validate(document()))
    }

    @Test
    fun `newer versions ask for an app update and unknown older ones are refused`() {
        assertEquals(
            ImportRejection.UnsupportedVersion(3, 1, requiresNewerApp = true),
            ImportValidator.validate(document(formatVersion = 3)),
        )
        assertEquals(
            ImportRejection.UnsupportedVersion(1, 7, requiresNewerApp = true),
            ImportValidator.validate(document(schemaVersion = 7)),
        )
        assertEquals(
            ImportRejection.UnsupportedVersion(0, 1, requiresNewerApp = false),
            ImportValidator.validate(document(formatVersion = 0)),
        )
    }

    @Test
    fun `counts over the limits are refused before anything else`() {
        val many = List(TransferLimits.MAX_CHECKLISTS + 1) { checklist("k$it", "List $it") }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.CHECKLISTS, TransferLimits.MAX_CHECKLISTS.toLong()),
            ImportValidator.validate(document(checklists = many, items = emptyList())),
        )
        val items = List(TransferLimits.MAX_ITEMS + 1) { item("i$it", "s1") }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.ITEMS, TransferLimits.MAX_ITEMS.toLong()),
            ImportValidator.validate(document(items = items)),
        )
    }

    @Test
    fun `a file without checklists has nothing to import`() {
        assertEquals(ImportRejection.NothingToImport, ImportValidator.validate(document(checklists = emptyList(), items = emptyList())))
    }

    @Test
    fun `refs must be well formed unique and resolve`() {
        val doc = document(
            checklists = listOf(
                checklist("k1", "A", TransferSection("s1", "c1", 0), TransferSection("s1", "c2", 1)),
                checklist("k1", "B", TransferSection("s3", "nope", 0)),
            ),
            items = listOf(item("i1", "s1"), item("i1", "s1"), item("bad ref!", "s1"), item("i4", "missing")),
        )
        val found = issues(doc).map { Triple(it.element, it.ref, it.problem) }
        assertTrue(Triple(TransferElement.SECTION, "s1", ImportProblem.DUPLICATE_REF) in found)
        assertTrue(Triple(TransferElement.CHECKLIST, "k1", ImportProblem.DUPLICATE_REF) in found)
        assertTrue(Triple(TransferElement.SECTION, "s3", ImportProblem.MISSING_REFERENCE) in found)
        assertTrue(Triple(TransferElement.ITEM, "i1", ImportProblem.DUPLICATE_REF) in found)
        assertTrue(Triple(TransferElement.ITEM, "bad ref!", ImportProblem.INVALID_REF) in found)
        assertTrue(Triple(TransferElement.ITEM, "i4", ImportProblem.MISSING_REFERENCE) in found)
    }

    @Test
    fun `a category appears at most once per checklist`() {
        val doc = document(checklists = listOf(checklist("k1", "A", TransferSection("s1", "c1", 0), TransferSection("s2", "c1", 1))), items = emptyList())
        assertEquals(listOf(ImportProblem.DUPLICATE_CATEGORY_IN_CHECKLIST), problems(doc))
    }

    @Test
    fun `a category needs exactly one of key and name and both are checked`() {
        val doc = document(
            categories = listOf(
                TransferCategory("c1", canonicalKey = "groceries", customName = "Both"),
                TransferCategory("c2"),
                TransferCategory("c3", canonicalKey = "Bad Key"),
                TransferCategory("c4", customName = "x".repeat(51), icon = "\u0007"),
            ),
            checklists = listOf(checklist("k1", "A")),
            items = emptyList(),
        )
        assertEquals(
            listOf(
                ImportProblem.CATEGORY_KEY_OR_NAME,
                ImportProblem.CATEGORY_KEY_OR_NAME,
                ImportProblem.INVALID_CANONICAL_KEY,
                ImportProblem.INVALID_FIELDS,
                ImportProblem.INVALID_ICON,
            ),
            problems(doc),
        )
        assertEquals(listOf(ValidationError.CATEGORY_NAME_TOO_LONG), issues(doc)[3].fieldErrors)
    }

    @Test
    fun `units must be custom and must not shadow built-in codes`() {
        val doc = document(
            units = listOf(
                TransferUnit("KG", "CUSTOM", "Kilo", allowsDecimal = true),
                TransferUnit("u2", "KG", "Other", allowsDecimal = true),
                TransferUnit("u3", "CUSTOM", " ", allowsDecimal = true),
            ),
            items = emptyList(),
        )
        assertEquals(
            listOf(ImportProblem.UNIT_REF_SHADOWS_BUILT_IN, ImportProblem.UNSUPPORTED_UNIT_CODE, ImportProblem.INVALID_FIELDS),
            problems(doc),
        )
    }

    @Test
    fun `item fields use the same rules as the UI`() {
        val doc = document(
            items = listOf(
                item("i1", "s1", quantity = "abc"),
                item("i2", "s1", quantity = "2.5", unit = "u1"),
                item("i3", "s1", quantity = "1", unit = "STONE"),
                item("i4", "s1", unit = "KG"),
                item("i5", "s1", name = " "),
                item("i6", "s1", quantity = "100000"),
                item("i7", "s1").copy(position = -1),
                item("i8", "s1").copy(displayNameLocale = "<script>"),
                item("i9", "s1").copy(canonicalKey = "DROP TABLE"),
            ),
        )
        val found = issues(doc).associate { it.ref to (it.problem to it.fieldErrors) }
        assertEquals(ImportProblem.INVALID_QUANTITY to emptyList<ValidationError>(), found["i1"])
        assertEquals(ImportProblem.INVALID_FIELDS to listOf(ValidationError.QUANTITY_MUST_BE_WHOLE), found["i2"])
        assertEquals(ImportProblem.UNKNOWN_UNIT to emptyList<ValidationError>(), found["i3"])
        assertEquals(ImportProblem.INVALID_FIELDS to listOf(ValidationError.UNIT_WITHOUT_QUANTITY), found["i4"])
        assertEquals(ImportProblem.INVALID_FIELDS to listOf(ValidationError.ITEM_NAME_BLANK), found["i5"])
        assertEquals(ImportProblem.INVALID_QUANTITY to emptyList<ValidationError>(), found["i6"])
        assertEquals(ImportProblem.INVALID_FIELDS to listOf(ValidationError.NEGATIVE_POSITION), found["i7"])
        assertEquals(ImportProblem.INVALID_LOCALE to emptyList<ValidationError>(), found["i8"])
        assertEquals(ImportProblem.INVALID_CANONICAL_KEY to emptyList<ValidationError>(), found["i9"])
    }

    @Test
    fun `control characters are stripped before validation so they cannot hide a blank title`() {
        val doc = document(checklists = listOf(checklist("k1", "\u0000\u0008\u001b")), items = emptyList())
        assertEquals(listOf(ValidationError.TITLE_BLANK), issues(doc).single().fieldErrors)
        assertEquals("Goa trip", TransferText.clean("Goa\u0000 trip\u0007"))
        assertEquals("a\nb", TransferText.clean("a\r\nb", multiline = true))
        assertEquals("a b", TransferText.clean("a\nb"))
        assertEquals("gnp.exe", TransferText.clean("‮gnp.exe‬⁦⁩"))
        // ZWJ and ZWNJ are part of Indic spelling and must survive.
        assertEquals("ಕ‍ಷ", TransferText.clean("ಕ‍ಷ"))
    }

    @Test
    fun `issue reports are capped but count everything`() {
        val items = List(120) { item("i$it", "missing") }
        val rejection = ImportValidator.validate(document(items = items)) as ImportRejection.Invalid
        assertEquals(TransferLimits.MAX_REPORTED_ISSUES, rejection.issues.size)
        assertEquals(120, rejection.totalIssues)
    }

    @Test
    fun `hostile refs are shortened in reports`() {
        val longRef = "r".repeat(1_500)
        val issue = issues(document(items = listOf(item(longRef, "s1", name = "Rice")))).single()
        assertEquals(TransferLimits.MAX_REF, issue.ref!!.length)
        assertEquals(ImportProblem.INVALID_REF, issue.problem)
    }

    @Test
    fun `invalid timestamps are reported`() {
        val doc = document(checklists = listOf(checklist("k1", "A").copy(createdAt = "yesterday")), items = emptyList())
        assertEquals(listOf(ImportProblem.INVALID_TIMESTAMP), problems(doc))
    }
}
