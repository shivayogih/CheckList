package com.dataloom.checklist.data

import com.dataloom.checklist.data.local.entity.MasterItemTranslationEntity
import com.dataloom.checklist.data.local.fts.SearchText
import com.dataloom.checklist.data.mapper.DisplayNames
import com.dataloom.checklist.data.repository.SparseOrder
import com.dataloom.checklist.data.repository.SparseOrder.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SparseOrderTest {

    private val list = listOf(Entry("a", 1000), Entry("b", 2000), Entry("c", 3000))

    @Test
    fun appendsInSteps() {
        assertEquals(1000, SparseOrder.after(null))
        assertEquals(listOf(4000, 5000), SparseOrder.appended(3000, 2))
    }

    @Test
    fun moveUsesTheGapBetweenNeighbours() {
        assertEquals(mapOf("c" to 1500), SparseOrder.move(list, "c", 1))
        assertEquals(mapOf("a" to 4000), SparseOrder.move(list, "a", 99))
        assertEquals(mapOf("c" to 0), SparseOrder.move(list, "c", -5))
    }

    @Test
    fun moveToSamePlaceOrUnknownIdChangesNothing() {
        assertEquals(emptyMap<String, Int>(), SparseOrder.move(list, "b", 1))
        assertEquals(emptyMap<String, Int>(), SparseOrder.move(list, "x", 0))
    }

    @Test
    fun renumbersWhenNoGapIsLeft() {
        val tight = listOf(Entry("a", 1000), Entry("b", 1001), Entry("c", 3000))

        assertEquals(mapOf("c" to 2000, "b" to 3000), SparseOrder.move(tight, "c", 1))
    }
}

class SearchTextTest {

    @Test
    fun normalizesCaseWidthAndSpaces() {
        assertEquals("rice flour", SearchText.normalize("  ＲＩＣＥ   Flour "))
    }

    @Test
    fun matchExpressionPrefixesEveryWordAndDropsSyntax() {
        assertEquals("basmati* ri*", SearchText.matchExpression("Basmati  RI"))
        assertEquals("rice*", SearchText.matchExpression("\"rice*\" - ("))
        assertEquals("ಅಕ್ಕಿ*", SearchText.matchExpression("ಅಕ್ಕಿ"))
        assertNull(SearchText.matchExpression(" *\"() "))
    }

    @Test
    fun ranksPhrases() {
        assertEquals(SearchText.Rank.EXACT, SearchText.rank("ಅಕ್ಕಿ\nakki", "akki"))
        assertEquals(SearchText.Rank.PREFIX, SearchText.rank("rice flour", "rice"))
        assertEquals(SearchText.Rank.CONTAINS, SearchText.rank("basmati rice", "rice"))
        assertEquals(SearchText.Rank.OTHER, SearchText.rank("rice flour", "flour rice"))
    }

    @Test
    fun rowsCoverTranslationsAndCustomName() {
        val rows = SearchText.rowsFor(
            masterItemId = "m",
            categoryId = "c",
            customName = "Our Rice",
            customNameLocale = "en",
            translations = listOf(MasterItemTranslationEntity("rice", "kn", "ಅಕ್ಕಿ", "Akki\nakki")),
        )

        assertEquals(listOf("ಅಕ್ಕಿ\nakki", "our rice"), rows.map { it.text })
        assertEquals(listOf("MASTER", "CUSTOM"), rows.map { it.refType })
    }
}

class DisplayNamesTest {

    private val translations = mapOf("en" to "Cooking oil", "kn" to "ಅಡುಗೆ ಎಣ್ಣೆ")

    @Test
    fun resolvesInPriorityOrder() {
        assertEquals("Our oil", DisplayNames.resolve("Our oil", "cooking_oil", translations, "kn"))
        assertEquals("ಅಡುಗೆ ಎಣ್ಣೆ", DisplayNames.resolve(null, "cooking_oil", translations, "kn-IN"))
        assertEquals("Cooking oil", DisplayNames.resolve(null, "cooking_oil", translations, "ta"))
        assertEquals("Cooking oil", DisplayNames.resolve(null, "cooking_oil", emptyMap(), "ta"))
        assertEquals("Cooking oil", DisplayNames.resolve("  ", "cooking_oil", emptyMap(), "en"))
    }

    @Test
    fun languageStripsRegion() {
        assertEquals("kn", DisplayNames.language("kn_IN"))
        assertEquals("en", DisplayNames.language(""))
        assertEquals(listOf("en"), DisplayNames.lookupLocales("en-GB"))
    }
}
