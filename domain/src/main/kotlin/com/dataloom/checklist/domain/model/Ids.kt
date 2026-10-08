package com.dataloom.checklist.domain.model

// Typed IDs: the compiler stops a CategoryId being passed where a ChecklistId is expected.
// Values are UUID strings (ADR-003) so imported data never collides with existing rows.

@JvmInline value class ChecklistId(val value: String)

@JvmInline value class CategoryId(val value: String)

@JvmInline value class MasterItemId(val value: String)

/** A category's placement inside one checklist (row of checklist_category). */
@JvmInline value class SectionId(val value: String)

@JvmInline value class ChecklistItemId(val value: String)
