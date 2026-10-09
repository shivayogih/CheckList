package com.dataloom.checklist.domain.transfer

import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.photo.PhotoLimits
import com.dataloom.checklist.domain.validation.CategoryValidator
import com.dataloom.checklist.domain.validation.ChecklistValidator
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.domain.validation.ItemValidator
import com.dataloom.checklist.domain.validation.UnitValidator
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.domain.validation.ValidationResult
import com.dataloom.checklist.domain.validation.codePointLength

/**
 * Structural and domain validation of a decoded file, without touching the database: limits,
 * refs (well-formed, unique, resolving), and every field through the same validators the UI uses.
 * Matching against existing categories, units and titles happens afterwards in [ImportPlanner].
 */
object ImportValidator {

    /**
     * Null when the document is acceptable. [archivePhotos] holds the lower-case keys
     * (`photos/p1.jpg`) of the photo entries of the zip the document came from; null for a plain
     * JSON file, which can therefore not carry photos.
     */
    fun validate(document: TransferDocument, archivePhotos: Set<String>? = null): ImportRejection? {
        checkVersions(document)?.let { return it }
        checkLimits(document)?.let { return it }
        if (document.checklists.isEmpty()) return ImportRejection.NothingToImport
        val issues = IssueCollector()
        val unitRefs = checkUnits(document.units, issues)
        val categoryRefs = checkCategories(document.categories, issues)
        val sectionRefs = checkChecklists(document.checklists, categoryRefs, issues)
        checkItems(document, sectionRefs, unitRefs, archivePhotos, issues)
        return issues.toRejection()
    }

    private fun checkVersions(document: TransferDocument): ImportRejection? {
        val format = document.formatVersion
        val schema = document.schemaVersion
        if (format in 1..TransferFormat.FORMAT_VERSION && schema == TransferFormat.SCHEMA_VERSION) return null
        val newer = format > TransferFormat.FORMAT_VERSION || schema > TransferFormat.SCHEMA_VERSION
        return ImportRejection.UnsupportedVersion(format, schema, requiresNewerApp = newer)
    }

    /** Defense in depth: the codec enforces these while parsing, but a document may come from elsewhere. */
    private fun checkLimits(document: TransferDocument): ImportRejection? {
        val sections = document.checklists.sumOf { it.sections.size }
        val limits = listOf(
            Triple(TransferLimit.CHECKLISTS, document.checklists.size, TransferLimits.MAX_CHECKLISTS),
            Triple(TransferLimit.ITEMS, document.items.size, TransferLimits.MAX_ITEMS),
            Triple(TransferLimit.CATEGORIES, document.categories.size, TransferLimits.MAX_CATEGORIES),
            Triple(TransferLimit.UNITS, document.units.size, TransferLimits.MAX_UNITS),
            Triple(TransferLimit.SECTIONS, sections, TransferLimits.MAX_SECTIONS),
            Triple(
                TransferLimit.SECTIONS,
                document.checklists.maxOfOrNull { it.sections.size } ?: 0,
                TransferLimits.MAX_SECTIONS_PER_CHECKLIST,
            ),
        )
        val photoCount = document.items.sumOf { it.photos.size }
        if (photoCount > TransferLimits.MAX_ARCHIVE_ENTRIES - 1) {
            val max = (TransferLimits.MAX_ARCHIVE_ENTRIES - 1).toLong()
            return ImportRejection.LimitExceeded(TransferLimit.PHOTOS, max)
        }
        val broken = limits.firstOrNull { (_, count, max) -> count > max } ?: return null
        return ImportRejection.LimitExceeded(broken.first, broken.third.toLong())
    }

    /** Returns ref -> unit definition (with a placeholder code) for valid custom units. */
    private fun checkUnits(units: List<TransferUnit>, issues: IssueCollector): Map<String, UnitDef> {
        val seen = HashSet<String>()
        val valid = LinkedHashMap<String, UnitDef>()
        units.forEachIndexed { index, unit ->
            val report = { problem: ImportProblem, errors: List<ValidationError> ->
                issues.add(ImportIssue(TransferElement.UNIT, index, TransferText.reportRef(unit.ref), problem, errors))
            }
            var ok = true
            when {
                !TransferText.isValidRef(unit.ref) -> { report(ImportProblem.INVALID_REF, emptyList()); ok = false }
                !seen.add(unit.ref) -> { report(ImportProblem.DUPLICATE_REF, emptyList()); ok = false }
                BuiltInUnits.byCode(UnitCode(unit.ref)) != null -> { report(ImportProblem.UNIT_REF_SHADOWS_BUILT_IN, emptyList()); ok = false }
            }
            if (unit.code != TransferFormat.CUSTOM_UNIT_CODE) {
                report(ImportProblem.UNSUPPORTED_UNIT_CODE, emptyList())
                ok = false
            }
            val label = UnitValidator.validateLabel(TransferText.clean(unit.label))
            if (label is ValidationResult.Invalid) {
                report(ImportProblem.INVALID_FIELDS, label.errors)
                ok = false
            }
            if (ok) valid[unit.ref] = UnitDef(UnitCode(UnitCode.CUSTOM_PREFIX + "import"), unit.allowsDecimal)
        }
        return valid
    }

    /** Returns the refs of well-formed categories. */
    private fun checkCategories(categories: List<TransferCategory>, issues: IssueCollector): Set<String> {
        val seen = HashSet<String>()
        val valid = HashSet<String>()
        categories.forEachIndexed { index, category ->
            val report = { problem: ImportProblem, errors: List<ValidationError> ->
                issues.add(ImportIssue(TransferElement.CATEGORY, index, TransferText.reportRef(category.ref), problem, errors))
            }
            var ok = true
            when {
                !TransferText.isValidRef(category.ref) -> { report(ImportProblem.INVALID_REF, emptyList()); ok = false }
                !seen.add(category.ref) -> { report(ImportProblem.DUPLICATE_REF, emptyList()); ok = false }
            }
            val key = category.canonicalKey
            val name = category.customName
            when {
                (key == null) == (name == null) -> { report(ImportProblem.CATEGORY_KEY_OR_NAME, emptyList()); ok = false }
                key != null && !TransferText.isValidCanonicalKey(key) -> { report(ImportProblem.INVALID_CANONICAL_KEY, emptyList()); ok = false }
                name != null -> {
                    val result = CategoryValidator.validateName(TransferText.clean(name))
                    if (result is ValidationResult.Invalid) {
                        report(ImportProblem.INVALID_FIELDS, result.errors)
                        ok = false
                    }
                }
            }
            if (category.icon != null && !TransferText.isValidIcon(category.icon)) {
                report(ImportProblem.INVALID_ICON, emptyList())
                ok = false
            }
            if (ok) valid += category.ref
        }
        return valid
    }

    /** Returns the refs of well-formed sections. */
    private fun checkChecklists(checklists: List<TransferChecklist>, categoryRefs: Set<String>, issues: IssueCollector): Set<String> {
        val checklistRefs = HashSet<String>()
        val sectionRefs = HashSet<String>()
        val validSections = HashSet<String>()
        checklists.forEachIndexed { index, checklist ->
            val report = { problem: ImportProblem, errors: List<ValidationError> ->
                issues.add(ImportIssue(TransferElement.CHECKLIST, index, TransferText.reportRef(checklist.ref), problem, errors))
            }
            when {
                !TransferText.isValidRef(checklist.ref) -> report(ImportProblem.INVALID_REF, emptyList())
                !checklistRefs.add(checklist.ref) -> report(ImportProblem.DUPLICATE_REF, emptyList())
            }
            val fields = ChecklistValidator.validate(
                TransferText.clean(checklist.title),
                TransferText.cleanOrNull(checklist.description, multiline = true),
            )
            if (fields is ValidationResult.Invalid) report(ImportProblem.INVALID_FIELDS, fields.errors)
            if (checklist.createdAt != null && !TransferText.isValidTimestamp(checklist.createdAt)) {
                report(ImportProblem.INVALID_TIMESTAMP, emptyList())
            }
            val categoriesInList = HashSet<String>()
            checklist.sections.forEachIndexed { sectionIndex, section ->
                val reportSection = { problem: ImportProblem, errors: List<ValidationError> ->
                    issues.add(ImportIssue(TransferElement.SECTION, sectionIndex, TransferText.reportRef(section.ref), problem, errors))
                }
                var ok = true
                when {
                    !TransferText.isValidRef(section.ref) -> { reportSection(ImportProblem.INVALID_REF, emptyList()); ok = false }
                    !sectionRefs.add(section.ref) -> { reportSection(ImportProblem.DUPLICATE_REF, emptyList()); ok = false }
                }
                when {
                    section.categoryRef !in categoryRefs -> { reportSection(ImportProblem.MISSING_REFERENCE, emptyList()); ok = false }
                    !categoriesInList.add(section.categoryRef) -> {
                        reportSection(ImportProblem.DUPLICATE_CATEGORY_IN_CHECKLIST, emptyList())
                        ok = false
                    }
                }
                if (section.order < 0) {
                    reportSection(ImportProblem.INVALID_FIELDS, listOf(ValidationError.NEGATIVE_POSITION))
                    ok = false
                }
                if (ok) validSections += section.ref
            }
        }
        return validSections
    }

    private fun checkItems(
        document: TransferDocument,
        sectionRefs: Set<String>,
        unitRefs: Map<String, UnitDef>,
        archivePhotos: Set<String>?,
        issues: IssueCollector,
    ) {
        val seen = HashSet<String>()
        val photoRefs = HashSet<String>()
        val photoFiles = HashSet<String>()
        document.items.forEachIndexed { index, item ->
            val report = { problem: ImportProblem, errors: List<ValidationError> ->
                issues.add(ImportIssue(TransferElement.ITEM, index, TransferText.reportRef(item.ref), problem, errors))
            }
            when {
                !TransferText.isValidRef(item.ref) -> report(ImportProblem.INVALID_REF, emptyList())
                !seen.add(item.ref) -> report(ImportProblem.DUPLICATE_REF, emptyList())
            }
            if (item.sectionRef !in sectionRefs) report(ImportProblem.MISSING_REFERENCE, emptyList())
            if (item.canonicalKey != null && !TransferText.isValidCanonicalKey(item.canonicalKey)) {
                report(ImportProblem.INVALID_CANONICAL_KEY, emptyList())
            }
            if (!TransferText.isValidLocale(item.displayNameLocale)) report(ImportProblem.INVALID_LOCALE, emptyList())

            val quantity = item.quantity?.let { Quantity.parse(it) }
            val quantityOk = item.quantity == null || quantity != null
            if (!quantityOk) report(ImportProblem.INVALID_QUANTITY, emptyList())

            val unit = item.unit?.let { resolveUnit(it, unitRefs) }
            if (item.unit != null && unit == null) report(ImportProblem.UNKNOWN_UNIT, emptyList())

            // A broken quantity or unit was reported above; validate the rest without it.
            val errors = buildList {
                val result = ItemValidator.validate(
                    TransferText.clean(item.displayName),
                    if (quantityOk) quantity else null,
                    unit,
                    TransferText.cleanOrNull(item.notes, multiline = true),
                )
                if (result is ValidationResult.Invalid) {
                    addAll(result.errors.filterNot { it == ValidationError.UNIT_WITHOUT_QUANTITY && !quantityOk })
                }
                if (item.position < 0) add(ValidationError.NEGATIVE_POSITION)
            }
            if (errors.isNotEmpty()) report(ImportProblem.INVALID_FIELDS, errors)
            checkPhotos(document.formatVersion, item, index, archivePhotos, photoRefs, photoFiles, issues)
        }
    }

    private fun checkPhotos(
        formatVersion: Int,
        item: TransferItem,
        itemIndex: Int,
        archivePhotos: Set<String>?,
        photoRefs: MutableSet<String>,
        photoFiles: MutableSet<String>,
        issues: IssueCollector,
    ) {
        if (item.photos.isEmpty()) return
        if (formatVersion < 2) {
            itemProblem(issues, item, itemIndex, ImportProblem.PHOTOS_NEED_FORMAT_2)
            return
        }
        if (item.photos.size > PhotoLimits.MAX_PER_ITEM) {
            itemProblem(issues, item, itemIndex, ImportProblem.TOO_MANY_PHOTOS)
        }
        item.photos.forEachIndexed { index, photo ->
            val report = { problem: ImportProblem, errors: List<ValidationError> ->
                val ref = TransferText.reportRef(photo.ref)
                issues.add(ImportIssue(TransferElement.PHOTO, index, ref, problem, errors))
            }
            when {
                !TransferText.isValidRef(photo.ref) -> report(ImportProblem.INVALID_REF, emptyList())
                !photoRefs.add(photo.ref) -> report(ImportProblem.DUPLICATE_REF, emptyList())
            }
            val key = ArchivePaths.photoKey(photo.file)
            when {
                key == null -> report(ImportProblem.INVALID_PHOTO_PATH, emptyList())
                !photoFiles.add(key) -> report(ImportProblem.DUPLICATE_PHOTO_FILE, emptyList())
                archivePhotos == null || key !in archivePhotos -> report(ImportProblem.MISSING_PHOTO_FILE, emptyList())
            }
            val caption = photo.caption?.let { captionText(it) }
            if (caption != null && caption.codePointLength() > PhotoLimits.CAPTION_MAX) {
                report(ImportProblem.INVALID_FIELDS, listOf(ValidationError.CAPTION_TOO_LONG))
            }
        }
    }

    private fun itemProblem(issues: IssueCollector, item: TransferItem, index: Int, problem: ImportProblem) {
        issues.add(ImportIssue(TransferElement.ITEM, index, TransferText.reportRef(item.ref), problem))
    }

    /** A caption is one line: control characters and line breaks are cleaned like other single-line text. */
    internal fun captionText(raw: String): String? = InputText.normalize(raw).ifEmpty { null }

    /** A built-in code wins only when no unit ref could mean the same text (refs never shadow codes). */
    internal fun resolveUnit(text: String, unitRefs: Map<String, UnitDef>): UnitDef? =
        BuiltInUnits.byCode(UnitCode(text)) ?: unitRefs[text]

    private class IssueCollector {
        private val issues = ArrayList<ImportIssue>()
        private var total = 0

        fun add(issue: ImportIssue) {
            total++
            if (issues.size < TransferLimits.MAX_REPORTED_ISSUES) issues += issue
        }

        fun toRejection(): ImportRejection? = if (total == 0) null else ImportRejection.Invalid(issues.toList(), total)
    }
}
