package com.dataloom.checklist.data.mapper

import com.dataloom.checklist.data.local.entity.CategoryEntity
import com.dataloom.checklist.data.local.entity.CategoryTranslationEntity
import com.dataloom.checklist.data.local.entity.ChecklistEntity
import com.dataloom.checklist.data.local.entity.ChecklistItemEntity
import com.dataloom.checklist.data.local.entity.ChecklistSummaryRow
import com.dataloom.checklist.data.local.entity.ChecklistWithSections
import com.dataloom.checklist.data.local.entity.MasterItemEntity
import com.dataloom.checklist.data.local.entity.MasterItemTranslationEntity
import com.dataloom.checklist.data.local.entity.UnitDefEntity
import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.model.Checklist
import com.dataloom.checklist.domain.model.ChecklistDetail
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.ChecklistItem
import com.dataloom.checklist.domain.model.ChecklistItemId
import com.dataloom.checklist.domain.model.ChecklistSection
import com.dataloom.checklist.domain.model.ChecklistSummary
import com.dataloom.checklist.domain.model.MasterItem
import com.dataloom.checklist.domain.model.MasterItemId
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef

/** canonical key -> (locale -> name) */
typealias TranslationIndex = Map<String, Map<String, String>>

@JvmName("categoryTranslationIndex")
fun List<CategoryTranslationEntity>.toIndex(): TranslationIndex =
    groupBy { it.canonicalKey }.mapValues { (_, rows) -> rows.associate { it.locale to it.name } }

@JvmName("itemTranslationIndex")
fun List<MasterItemTranslationEntity>.toIndex(): TranslationIndex =
    groupBy { it.canonicalKey }.mapValues { (_, rows) -> rows.associate { it.locale to it.name } }

fun ChecklistEntity.toDomain(): Checklist = Checklist(
    id = ChecklistId(id),
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
)

fun ChecklistSummaryRow.toDomain(): ChecklistSummary = ChecklistSummary(
    checklist = checklist.toDomain(),
    totalItems = totalItems,
    completedItems = completedItems,
)

fun CategoryEntity.toDomain(translations: TranslationIndex, locale: String): Category = Category(
    id = CategoryId(id),
    canonicalKey = canonicalKey,
    customName = customName,
    displayName = DisplayNames.resolve(customName, canonicalKey, canonicalKey?.let { translations[it] }.orEmpty(), locale),
    iconKey = iconKey,
    isCustom = isCustom,
    isHidden = isHidden,
)

fun MasterItemEntity.toDomain(translations: TranslationIndex, locale: String): MasterItem = MasterItem(
    id = MasterItemId(id),
    categoryId = CategoryId(categoryId),
    canonicalKey = canonicalKey,
    customName = customName,
    displayName = DisplayNames.resolve(customName, canonicalKey, translations[canonicalKey].orEmpty(), locale),
    defaultUnit = defaultUnitCode?.let(::UnitCode),
    isCustom = isCustom,
    isHidden = isHidden,
    useCount = useCount,
)

fun UnitDefEntity.toDomain(): UnitDef = UnitDef(
    code = UnitCode(code),
    allowsDecimal = allowsDecimal,
    customLabel = customLabel,
    sortOrder = sortOrder,
)

fun ChecklistItemEntity.toDomain(): ChecklistItem = ChecklistItem(
    id = ChecklistItemId(id),
    sectionId = SectionId(checklistCategoryId),
    masterItemId = masterItemId?.let(::MasterItemId),
    canonicalKey = canonicalKey,
    displayName = displayName,
    displayNameLocale = displayNameLocale,
    // Only positive amounts are ever written; anything else reads as "no quantity".
    quantity = quantityMilli?.takeIf { it > 0 }?.let(Quantity::fromMilli),
    unit = unitCode?.let(::UnitCode),
    notes = notes,
    isCompleted = isCompleted,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Sections by display order, items by position; ties broken by creation time and ID so order is stable. */
fun ChecklistWithSections.toDomain(categoryTranslations: TranslationIndex, locale: String): ChecklistDetail =
    ChecklistDetail(
        checklist = checklist.toDomain(),
        sections = sections
            .sortedWith(compareBy({ it.section.displayOrder }, { it.section.createdAt }, { it.section.id }))
            .map { section ->
                ChecklistSection(
                    id = SectionId(section.section.id),
                    checklistId = ChecklistId(section.section.checklistId),
                    category = section.category.toDomain(categoryTranslations, locale),
                    displayOrder = section.section.displayOrder,
                    items = section.items
                        .sortedWith(compareBy({ it.position }, { it.createdAt }, { it.id }))
                        .map { it.toDomain() },
                )
            },
    )
