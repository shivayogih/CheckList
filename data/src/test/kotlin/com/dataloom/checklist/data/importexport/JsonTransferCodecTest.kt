package com.dataloom.checklist.data.importexport

import com.dataloom.checklist.domain.transfer.DecodeResult
import com.dataloom.checklist.domain.transfer.ImportProblem
import com.dataloom.checklist.domain.transfer.ImportRejection
import com.dataloom.checklist.domain.transfer.ImportValidator
import com.dataloom.checklist.domain.transfer.TransferCategory
import com.dataloom.checklist.domain.transfer.TransferDocument
import com.dataloom.checklist.domain.transfer.TransferItem
import com.dataloom.checklist.domain.transfer.TransferLimit
import com.dataloom.checklist.domain.transfer.TransferLimits
import com.dataloom.checklist.domain.transfer.TransferPhoto
import com.dataloom.checklist.domain.transfer.TransferUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden files live in src/test/resources/transfer (see docs/import-export-format.md). */
class JsonTransferCodecTest {

    private val codec = JsonTransferCodec()

    private fun golden(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/transfer/$name")) { "missing golden file $name" }.use { it.readBytes() }

    private fun decoded(bytes: ByteArray): TransferDocument = (codec.decode(bytes) as DecodeResult.Decoded).document

    private fun rejection(text: String): ImportRejection = (codec.decode(text.toByteArray()) as DecodeResult.Rejected).rejection

    private fun envelope(checklists: String = "[]", items: String = "[]", extra: String = ""): String =
        """{"formatVersion":1,"schemaVersion":1,
           "metadata":{"app":"CheckList","appVersion":"1","exportedAt":"2026-10-08T04:00:00Z","locale":"en","includesProfile":false},
           "units":[],"categories":[],"checklists":$checklists,"items":$items,"profile":null$extra}"""

    @Test
    fun fullGoldenFileDecodesEveryField() {
        val doc = decoded(golden("valid-full.json"))
        assertEquals(1, doc.formatVersion)
        assertEquals("kn", doc.metadata.locale)
        assertEquals(listOf(TransferUnit("u1", "CUSTOM", "Bundle", allowsDecimal = false)), doc.units)
        assertEquals(TransferCategory("c2", customName = "Pooja Items", icon = "temple"), doc.categories[1])
        assertEquals(listOf("s1", "s2"), doc.checklists.single().sections.map { it.ref })
        assertEquals(
            TransferItem(
                ref = "i2", sectionRef = "s1", displayName = "चावल का आटा", displayNameLocale = "hi",
                quantity = "2.5", unit = "KG", notes = "ಮಧ್ಯಮ ಗಾತ್ರ\nచిన్న ప్యాకెట్", completed = true, position = 1,
            ),
            doc.items[1],
        )
        assertFalse(doc.profilePresent)
        assertNull(ImportValidator.validate(doc))
    }

    @Test
    fun version2GoldenFileDecodesPhotosAndTheyRoundTrip() {
        val doc = decoded(golden("valid-photos-v2.json"))
        assertEquals(2, doc.formatVersion)
        assertEquals(
            listOf(TransferPhoto("p1", "photos/p1.jpg", "கடையின் முன்பக்கம்"), TransferPhoto("p2", "photos/p2.jpg", null)),
            doc.items[0].photos,
        )
        assertTrue(doc.items[1].photos.isEmpty())
        assertNull(ImportValidator.validate(doc, setOf("photos/p1.jpg", "photos/p2.jpg")))
        assertEquals(doc, decoded(codec.encode(doc)))
        val text = codec.encode(doc).decodeToString()
        assertTrue(text.contains("\"formatVersion\": 2"))
        assertTrue(text.contains("\"file\": \"photos/p1.jpg\""))
    }

    @Test
    fun itemsWithoutPhotosWriteNoPhotosKeySoVersion1FilesAreUnchanged() {
        val text = codec.encode(decoded(golden("valid-full.json"))).decodeToString()
        assertFalse(text.contains("photos"))
    }

    @Test
    fun aVersion1FileWithPhotosDecodesButTheValidatorRefusesIt() {
        val text = String(golden("valid-photos-v2.json")).replace("\"formatVersion\": 2", "\"formatVersion\": 1")
        val doc = decoded(text.toByteArray())
        val issues = (ImportValidator.validate(doc, setOf("photos/p1.jpg", "photos/p2.jpg")) as ImportRejection.Invalid).issues
        assertEquals(listOf(ImportProblem.PHOTOS_NEED_FORMAT_2), issues.map { it.problem })
    }

    @Test
    fun photoFieldsAreStrict() {
        val item = """{"ref":"i1","sectionRef":"s1","displayName":"x","displayNameLocale":"en","photos":[{"ref":"p1","file":"photos/p1.jpg","extra":1}]}"""
        assertEquals(ImportRejection.Malformed, rejection(envelope(items = "[$item]").replace("\"formatVersion\":1", "\"formatVersion\":2")))
    }

    @Test
    fun minimalGoldenFileUsesDefaults() {
        val doc = decoded(golden("valid-minimal.json"))
        val item = doc.items.single()
        assertEquals(null, item.quantity)
        assertFalse(item.completed)
        assertEquals(0, item.position)
        assertFalse(doc.checklists.single().archived)
        assertNull(ImportValidator.validate(doc))
    }

    @Test
    fun encodeThenDecodeIsLossless() {
        val doc = decoded(golden("valid-full.json"))
        assertEquals(doc, decoded(codec.encode(doc)))
    }

    @Test
    fun encodedFileIsReadableUtf8WithExplicitNulls() {
        val text = codec.encode(decoded(golden("valid-full.json"))).decodeToString()
        assertTrue(text.contains("\"displayName\": \"ಅಕ್ಕಿ\""))
        assertTrue(text.contains("\"profile\": null"))
        assertTrue(text.contains("\"notes\": null"))
        assertTrue(text.startsWith("{\n    \"formatVersion\": 1,\n    \"schemaVersion\": 1,"))
    }

    @Test
    fun newerVersionIsRejectedEvenWithAnUnknownShape() {
        assertEquals(
            DecodeResult.Rejected(ImportRejection.UnsupportedVersion(3, 3, requiresNewerApp = true)),
            codec.decode(golden("future-version.json")),
        )
    }

    @Test
    fun versionsThatNeverExistedAreRejected() {
        assertEquals(
            ImportRejection.UnsupportedVersion(0, 1, requiresNewerApp = false),
            rejection(envelope().replace("\"formatVersion\":1", "\"formatVersion\":0")),
        )
        assertEquals(ImportRejection.Malformed, rejection("""{"schemaVersion":1}"""))
        assertEquals(ImportRejection.Malformed, rejection("""{"formatVersion":"1","schemaVersion":1}"""))
    }

    @Test
    fun olderVersionsAreUpgradedThroughMigrators() {
        // A made-up schema 0 that called the item list "entries".
        val migrator = object : JsonMigrator {
            override val fromFormatVersion = 1
            override val fromSchemaVersion = 0
            override fun migrate(root: JsonObject): JsonObject {
                val fields = root.toMutableMap()
                fields["items"] = fields.remove("entries")!!
                fields["schemaVersion"] = JsonPrimitive(1)
                return JsonObject(fields)
            }
        }
        val old = envelope().replace("\"schemaVersion\":1", "\"schemaVersion\":0").replace("\"items\":", "\"entries\":")
        val upgraded = JsonTransferCodec(listOf(migrator)).decode(old.toByteArray()) as DecodeResult.Decoded
        assertEquals(1, upgraded.document.schemaVersion)
        assertEquals(ImportRejection.UnsupportedVersion(1, 0, requiresNewerApp = false), rejection(old))
    }

    @Test
    fun malformedInputIsRejectedWithoutThrowing() {
        val inputs = listOf(
            "",
            "not json",
            "[]",
            "null",
            envelope().dropLast(10),
            envelope() + " trailing",
            envelope(extra = ""","surprise":true"""),
            envelope().replace("\"units\":[]", "\"units\":{}"),
            envelope(checklists = """[{"ref":"k1","title":7,"sections":[]}]"""),
            envelope(checklists = """[{"ref":"k1","title":"A","sections":[],}]"""),
            envelope(checklists = """[{"ref":"k1","title":"A","archived":"yes","sections":[]}]"""),
            envelope(checklists = """[{"ref":"k1","title":"A","sections":[{"ref":"s1","categoryRef":"c1","order":1e99}]}]"""),
            envelope(checklists = """[/* comment */]"""),
            envelope(checklists = """[{ref:"k1",title:"A",sections:[]}]"""),
            envelope(checklists = """[{"ref":"k1","title":"A","sections":[]}]""") .replace("\"items\":[]", "\"items\":[{}]"),
        )
        inputs.forEach { input ->
            assertEquals("input: $input", ImportRejection.Malformed, rejection(input))
        }
    }

    @Test
    fun invalidUtf8IsRejectedButAByteOrderMarkIsFine() {
        val bytes = golden("valid-minimal.json")
        val broken = bytes.copyOf().also { it[it.indexOf('T'.code.toByte())] = 0xC3.toByte() }
        assertEquals(DecodeResult.Rejected(ImportRejection.Malformed), codec.decode(broken))
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes
        assertTrue(codec.decode(withBom) is DecodeResult.Decoded)
    }

    @Test
    fun arraysOverTheLimitStopTheParse() {
        val checklists = List(TransferLimits.MAX_CHECKLISTS + 1) { """{"ref":"k$it","title":"T","sections":[]}""" }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.CHECKLISTS, TransferLimits.MAX_CHECKLISTS.toLong()),
            rejection(envelope(checklists = checklists.joinToString(",", "[", "]"))),
        )
        val items = List(TransferLimits.MAX_ITEMS + 1) { """{"ref":"i$it","sectionRef":"s1","displayName":"x","displayNameLocale":"en"}""" }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.ITEMS, TransferLimits.MAX_ITEMS.toLong()),
            rejection(envelope(items = items.joinToString(",", "[", "]"))),
        )
        val sections = List(TransferLimits.MAX_SECTIONS_PER_CHECKLIST + 1) { """{"ref":"s$it","categoryRef":"c1","order":$it}""" }
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.SECTIONS, TransferLimits.MAX_SECTIONS_PER_CHECKLIST.toLong()),
            rejection(envelope(checklists = """[{"ref":"k1","title":"T","sections":${sections.joinToString(",", "[", "]")}}]""")),
        )
    }

    @Test
    fun overlongStringsStopTheParse() {
        val title = "ಅ".repeat(TransferLimits.MAX_RAW_TEXT + 1)
        assertEquals(
            ImportRejection.LimitExceeded(TransferLimit.TEXT_LENGTH, TransferLimits.MAX_RAW_TEXT.toLong()),
            rejection(envelope(checklists = """[{"ref":"k1","title":"$title","sections":[]}]""")),
        )
    }

    @Test
    fun deeplyNestedJsonIsRejectedWithoutOverflowingTheStack() {
        val depth = 50_000
        val nested = "[".repeat(depth) + "]".repeat(depth)
        assertEquals(ImportRejection.Malformed, rejection(envelope().replace("\"profile\":null", "\"profile\":$nested")))
        assertEquals(ImportRejection.Malformed, rejection(envelope(checklists = nested)))
        // Brackets inside strings are text, not nesting.
        val bracketsInText = "[".repeat(100) + "\\\"" + "{".repeat(100)
        val doc = decoded(envelope(checklists = """[{"ref":"k1","title":"$bracketsInText","sections":[]}]""").toByteArray())
        assertEquals("[".repeat(100) + "\"" + "{".repeat(100), doc.checklists.single().title)
        // A small nested profile is fine and is skipped on import.
        assertTrue(decoded(golden("hostile-strings.json")).profilePresent)
    }

    @Test
    fun brokenReferencesAreFoundByTheValidator() {
        val rejection = ImportValidator.validate(decoded(golden("broken-refs.json"))) as ImportRejection.Invalid
        val problems = rejection.issues.map { it.ref to it.problem }
        assertEquals(
            listOf(
                "c1" to ImportProblem.DUPLICATE_REF,
                "s1" to ImportProblem.MISSING_REFERENCE,
                "s2" to ImportProblem.DUPLICATE_REF,
                "s2" to ImportProblem.DUPLICATE_CATEGORY_IN_CHECKLIST,
                "i1" to ImportProblem.MISSING_REFERENCE,
                "i2" to ImportProblem.UNKNOWN_UNIT,
                "i2" to ImportProblem.DUPLICATE_REF,
            ),
            problems,
        )
    }

    @Test
    fun hostileStringsParseAsPlainData() {
        val doc = decoded(golden("hostile-strings.json"))
        assertTrue(doc.profilePresent)
        assertEquals("'; DROP TABLE checklist; --", doc.metadata.appVersion)
        assertNull(ImportValidator.validate(doc))
    }
}
