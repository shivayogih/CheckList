package com.dataloom.checklist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dataloom.checklist.data.local.database.CheckListDatabase
import com.dataloom.checklist.data.local.database.CheckListDatabaseCallback
import com.dataloom.checklist.data.seed.SeedLoader
import com.dataloom.checklist.data.seed.SeedSource
import com.dataloom.checklist.domain.common.Clock
import com.dataloom.checklist.domain.common.IdGenerator

class FakeClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

/** Readable, predictable IDs: "id-1", "id-2"... */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

class FakeSeedSource(
    var catalog: String? = TestCatalog.CATALOG_V1,
    var translations: List<String> = listOf(TestCatalog.EN, TestCatalog.KN, TestCatalog.HI),
) : SeedSource {
    override fun catalog(): String? = catalog
    override fun translations(): List<String> = translations
}

/** In-memory database with the production callback (schema triggers, optional seeding). */
fun inMemoryDatabase(seedLoader: SeedLoader? = null): CheckListDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CheckListDatabase::class.java)
        .addCallback(CheckListDatabaseCallback(seedLoader))
        .allowMainThreadQueries()
        .build()

/** A small catalog covering exact/prefix/contains ranking and three languages. */
object TestCatalog {

    const val CATALOG_V1 = """
    {
      "seedVersion": 1,
      "units": [
        {"code": "KG", "allowsDecimal": true, "sortOrder": 10},
        {"code": "PIECE", "allowsDecimal": false, "sortOrder": 60}
      ],
      "categories": [
        {"key": "groceries", "icon": "🛒", "sortOrder": 10},
        {"key": "vegetables", "icon": "🥕", "sortOrder": 20},
        {"key": "cooking_essentials", "icon": "🍳", "sortOrder": 30}
      ],
      "items": [
        {"key": "rice", "category": "groceries", "defaultUnit": "KG"},
        {"key": "rice_flour", "category": "groceries", "defaultUnit": "KG"},
        {"key": "basmati_rice", "category": "groceries", "defaultUnit": "KG"},
        {"key": "tomato", "category": "vegetables", "defaultUnit": "KG"},
        {"key": "cooking_oil", "category": "cooking_essentials", "defaultUnit": "LITRE"}
      ]
    }
    """

    /** v2: new icon, tomato moves to groceries and counts in pieces, one new item. */
    const val CATALOG_V2 = """
    {
      "seedVersion": 2,
      "units": [
        {"code": "KG", "allowsDecimal": true, "sortOrder": 10},
        {"code": "PIECE", "allowsDecimal": false, "sortOrder": 60}
      ],
      "categories": [
        {"key": "groceries", "icon": "🧺", "sortOrder": 10},
        {"key": "vegetables", "icon": "🥦", "sortOrder": 20},
        {"key": "cooking_essentials", "icon": "🍳", "sortOrder": 30}
      ],
      "items": [
        {"key": "rice", "category": "groceries", "defaultUnit": "KG"},
        {"key": "rice_flour", "category": "groceries", "defaultUnit": "PIECE"},
        {"key": "basmati_rice", "category": "groceries", "defaultUnit": "KG"},
        {"key": "tomato", "category": "groceries", "defaultUnit": "PIECE"},
        {"key": "cooking_oil", "category": "cooking_essentials", "defaultUnit": "LITRE"},
        {"key": "onion", "category": "vegetables", "defaultUnit": "KG"}
      ]
    }
    """

    const val EN = """
    {
      "locale": "en",
      "categories": {"groceries": "Groceries", "vegetables": "Vegetables"},
      "items": {
        "rice": {"name": "Rice"},
        "rice_flour": {"name": "Rice flour"},
        "basmati_rice": {"name": "Basmati rice"},
        "tomato": {"name": "Tomato"},
        "onion": {"name": "Onion"}
      }
    }
    """

    const val KN = """
    {
      "locale": "kn",
      "categories": {"groceries": "ದಿನಸಿ"},
      "items": {
        "rice": {"name": "ಅಕ್ಕಿ", "aliases": ["akki"]},
        "rice_flour": {"name": "ಅಕ್ಕಿ ಹಿಟ್ಟು", "aliases": ["akki hittu"]},
        "tomato": {"name": "ಟೊಮೆಟೊ"}
      }
    }
    """

    const val HI = """
    {
      "locale": "hi",
      "categories": {"groceries": "किराना"},
      "items": {
        "rice": {"name": "चावल", "aliases": ["chawal"]}
      }
    }
    """
}
