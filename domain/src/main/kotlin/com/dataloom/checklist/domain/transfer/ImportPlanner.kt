package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.ItemValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import com.dataloom.checklist.domain.validation.codePointLength
import kotlinx.coroutines.flow.first

/** Where an imported section's category comes from. */
internal sealed interface CategoryTarget {
    data class Existing(val id: CategoryId) : CategoryTarget

    /** Index into [ImportPlan.newCategories]. */
    data class New(val index: Int) : CategoryTarget
}

internal sealed interface UnitTarget {
    data class Existing(val code: UnitCode) : UnitTarget

    /** Index into [ImportPlan.newUnits]. */
    data class New(val index: Int) : UnitTarget
}

internal data class NewCategory(val name: String, val icon: String)

internal data class NewUnit(val label: String, val allowsDecimal: Boolean)

internal data class PlannedItem(
    val canonicalKey: String?,
    val name: String,
    val locale: String,
    val quantity: Quantity?,
    val unit: UnitTarget?,
    val notes: String?,
    val completed: Boolean,
    val photos: List<PlannedPhoto> = emptyList(),
)

/** [key] is the lower-case archive path (`photos/p1.jpg`) of the raw image. */
internal data class PlannedPhoto(val key: String, val caption: String?)

internal data class PlannedSection(val category: CategoryTarget, val items: List<PlannedItem>)

internal data class PlannedChecklist(
    val originalTitle: String,
    val title: String,
    val description: String?,
    val archived: Boolean,
    val sections: List<PlannedSection>,
)

/** Everything an import will write, already matched against the current database. */
internal data class ImportPlan(
    val newCategories: List<NewCategory>,
    val matchedCategoryCount: Int,
    val newUnits: List<NewUnit>,
    val matchedUnitCount: Int,
    val checklists: List<PlannedChecklist>,
) {
    val itemCount: Int get() = checklists.sumOf { list -> list.sections.sumOf { it.items.size } }
    val photoCount: Int
        get() = checklists.sumOf { list -> list.sections.sumOf { s -> s.items.sumOf { it.photos.size } } }
    val completedItemCount: Int get() = checklists.sumOf { list -> list.sections.sumOf { s -> s.items.count { it.completed } } }
    val renames: List<ChecklistRename>
        get() = checklists.filter { it.title != it.originalTitle }.map { ChecklistRename(it.originalTitle, it.title) }
}

/**
 * Matches a validated document against the database (section 20.2):
 * - a `canonicalKey` maps to the seeded category with that key; a key this device does not know
 *   becomes a custom category named after the key,
 * - a `customName` maps to an existing category with that name, ignoring case (custom categories
 *   first, then any category whose name in [locale] matches), otherwise one new category is created
 *   however many times the name appears in the file,
 * - a custom unit maps to an existing custom unit with the same label, ignoring case, otherwise one
 *   new unit per label; items are re-validated against the unit they will really use,
 * - a title that already exists, or repeats within the file, gets " (2)", " (3)"...
 *
 * Only categories and units that some section or item uses are created; unused entries are ignored.
 */
internal class ImportPlanner(
    private val checklists: ChecklistRepository,
    private val catalog: CatalogRepository,
) {

    sealed interface Outcome {
        data class Planned(val plan: ImportPlan) : Outcome

        data class Rejected(val rejection: ImportRejection) : Outcome
    }

    suspend fun plan(document: TransferDocument, locale: String): Outcome {
        val categories = catalog.observeCategories(locale, includeHidden = true).first()
        val units = catalog.observeUnits().first()
        val issues = mutableListOf<ImportIssue>()

        val categoryResolver = CategoryResolver(categories)
        val categoryByRef = document.categories.associateBy { it.ref }
        val unitResolver = UnitResolver(units, document.units.associateBy { it.ref })
        val itemsBySection = document.items.withIndex().groupBy { it.value.sectionRef }

        val usedTitles = HashSet<String>()
        val planned = document.checklists.map { checklist ->
            val fields = (ChecklistValidator.validate(
                TransferText.clean(checklist.title),
                TransferText.cleanOrNull(checklist.description, multiline = true),
            ) as ValidationResult.Valid).value
            val seenTargets = HashSet<CategoryTarget>()
            val sections = checklist.sections.withIndex()
                .sortedWith(compareBy({ it.value.order }, { it.index }))
                .map { (sectionIndex, section) ->
                    val target = categoryResolver.resolve(categoryByRef.getValue(section.categoryRef))
                    if (!seenTargets.add(target)) {
                        issues += ImportIssue(
                            TransferElement.SECTION,
                            sectionIndex,
                            TransferText.reportRef(section.ref),
                            ImportProblem.DUPLICATE_CATEGORY_IN_CHECKLIST,
                        )
                    }
                    val items = itemsBySection[section.ref].orEmpty()
                        .sortedWith(compareBy({ it.value.position }, { it.index }))
                        .mapNotNull { (itemIndex, item) -> planItem(itemIndex, item, unitResolver, issues) }
                    PlannedSection(target, items)
                }
            PlannedChecklist(
                originalTitle = fields.title,
                title = uniqueTitle(fields.title, usedTitles),
                description = fields.description,
                archived = checklist.archived,
                sections = sections,
            )
        }
        if (issues.isNotEmpty()) {
            return Outcome.Rejected(ImportRejection.Invalid(issues.take(TransferLimits.MAX_REPORTED_ISSUES), issues.size))
        }
        return Outcome.Planned(
            ImportPlan(
                newCategories = categoryResolver.newCategories,
                matchedCategoryCount = categoryResolver.matchedCount,
                newUnits = unitResolver.newUnits,
                matchedUnitCount = unitResolver.matchedCount,
                checklists = planned,
            ),
        )
    }

    private fun planItem(index: Int, item: TransferItem, units: UnitResolver, issues: MutableList<ImportIssue>): PlannedItem? {
        val quantity = item.quantity?.let { Quantity.parse(it) }
        val resolved = item.unit?.let { units.resolve(it) }
        if (item.unit != null && resolved == null) {
            // A built-in code this database does not have (an older seed); never drop it silently.
            issues += ImportIssue(TransferElement.ITEM, index, TransferText.reportRef(item.ref), ImportProblem.UNKNOWN_UNIT)
            return null
        }
        val result = ItemValidator.validate(
            TransferText.clean(item.displayName),
            quantity,
            resolved?.second,
            TransferText.cleanOrNull(item.notes, multiline = true),
        )
        return when (result) {
            is ValidationResult.Invalid -> {
                // Only possible when an existing unit with the same label counts whole things.
                issues += ImportIssue(TransferElement.ITEM, index, TransferText.reportRef(item.ref), ImportProblem.INVALID_FIELDS, result.errors)
                null
            }
            is ValidationResult.Valid -> PlannedItem(
                canonicalKey = item.canonicalKey,
                name = result.value.name,
                locale = item.displayNameLocale,
                quantity = result.value.quantity,
                unit = resolved?.first,
                notes = result.value.notes,
                completed = item.completed,
                photos = item.photos.mapNotNull { photo ->
                    ArchivePaths.photoKey(photo.file)?.let {
                        PlannedPhoto(it, photo.caption?.let(ImportValidator::captionText))
                    }
                },
            )
        }
    }

    /** "Goa Trip" -> "Goa Trip (2)" when taken in the database or earlier in this import. */
    private suspend fun uniqueTitle(title: String, usedTitles: MutableSet<String>): String {
        var candidate = title
        var number = 1
        while (TransferText.matchKey(candidate) in usedTitles || checklists.titleExists(candidate)) {
            number++
            candidate = numberedTitle(title, number)
        }
        usedTitles += TransferText.matchKey(candidate)
        return candidate
    }

    private class CategoryResolver(private val existing: List<Category>) {
        val newCategories = mutableListOf<NewCategory>()
        private val newByName = HashMap<String, Int>()
        private val matched = HashSet<CategoryId>()
        val matchedCount: Int get() = matched.size

        fun resolve(category: TransferCategory): CategoryTarget {
            category.canonicalKey?.let { key ->
                existing.firstOrNull { it.canonicalKey == key }?.let { return existingTarget(it.id) }
                return byName(TransferText.humanize(key).ifBlank { key }.take(FieldLimits.CATEGORY_NAME_MAX), category.icon)
            }
            return byName(TransferText.clean(category.customName!!).trim(), category.icon)
        }

        private fun byName(name: String, icon: String?): CategoryTarget {
            val key = TransferText.matchKey(name)
            val match = existing.firstOrNull { it.isCustom && it.customName?.let(TransferText::matchKey) == key }
                ?: existing.firstOrNull { TransferText.matchKey(it.displayName) == key }
            if (match != null) return existingTarget(match.id)
            val index = newByName.getOrPut(key) {
                newCategories += NewCategory(name, icon ?: DEFAULT_CATEGORY_ICON)
                newCategories.lastIndex
            }
            return CategoryTarget.New(index)
        }

        private fun existingTarget(id: CategoryId): CategoryTarget {
            matched += id
            return CategoryTarget.Existing(id)
        }
    }

    private class UnitResolver(existing: List<UnitDef>, private val fileUnits: Map<String, TransferUnit>) {
        private val builtIn = existing.filterNot { it.isCustom }.associateBy { it.code.value }
        private val customByLabel = existing.filter { it.isCustom && it.customLabel != null }
            .associateBy { TransferText.matchKey(it.customLabel!!) }
        val newUnits = mutableListOf<NewUnit>()
        private val newByLabel = HashMap<String, Int>()
        private val matched = HashSet<UnitCode>()
        val matchedCount: Int get() = matched.size

        /** The target and the definition items are validated against; null for an unknown built-in code. */
        fun resolve(text: String): Pair<UnitTarget, UnitDef>? {
            builtIn[text]?.let { return UnitTarget.Existing(it.code) to it }
            val fileUnit = fileUnits[text] ?: return null
            val label = TransferText.clean(fileUnit.label).trim()
            val key = TransferText.matchKey(label)
            customByLabel[key]?.let { unit ->
                matched += unit.code
                return UnitTarget.Existing(unit.code) to unit
            }
            val index = newByLabel.getOrPut(key) {
                newUnits += NewUnit(label, fileUnit.allowsDecimal)
                newUnits.lastIndex
            }
            val definition = newUnits[index]
            return UnitTarget.New(index) to UnitDef(UnitCode(UnitCode.CUSTOM_PREFIX + "new"), definition.allowsDecimal)
        }
    }

    companion object {
        /** Icon of the seeded "Other" category; used when a file names no icon. */
        const val DEFAULT_CATEGORY_ICON = "📦"

        /** Appends " (n)", shortening the base so the title stays within the limit. */
        fun numberedTitle(title: String, number: Int): String {
            val suffix = " ($number)"
            val room = FieldLimits.TITLE_MAX - suffix.length
            val base = if (title.codePointLength() <= room) title else title.takeCodePoints(room).trimEnd()
            return base + suffix
        }

        private fun String.takeCodePoints(count: Int): String = substring(0, offsetByCodePoints(0, count))
    }
}
