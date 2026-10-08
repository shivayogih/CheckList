package com.dataloom.checklist.ai.fixtures

import com.dataloom.checklist.ai.AiAssistant
import com.dataloom.checklist.ai.executor.ActionPlanExecutor
import com.dataloom.checklist.ai.mapper.CatalogLookup
import com.dataloom.checklist.ai.mapper.PlanValidator
import com.dataloom.checklist.ai.model.ContextSnapshot
import com.dataloom.checklist.ai.parser.CommandGrammar
import com.dataloom.checklist.ai.parser.LanguagePacks
import com.dataloom.checklist.ai.parser.OfflineCommandParser
import com.dataloom.checklist.ai.policy.AiSettings
import com.dataloom.checklist.ai.policy.AiSettingsSource
import com.dataloom.checklist.ai.policy.ConfirmationPolicy
import com.dataloom.checklist.ai.service.AIService
import com.dataloom.checklist.ai.tools.ReadToolRunner
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.ChecklistId
import com.dataloom.checklist.domain.model.NewChecklistItem
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.SectionId
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.usecase.AddCategoriesToChecklistUseCase
import com.dataloom.checklist.domain.usecase.AddCustomItemUseCase
import com.dataloom.checklist.domain.usecase.AddMasterItemsToSectionUseCase
import com.dataloom.checklist.domain.usecase.CreateChecklistUseCase
import com.dataloom.checklist.domain.usecase.DeleteChecklistItemUseCase
import com.dataloom.checklist.domain.usecase.ObserveCategoriesUseCase
import com.dataloom.checklist.domain.usecase.ObserveChecklistDetailUseCase
import com.dataloom.checklist.domain.usecase.ObserveMasterItemsUseCase
import com.dataloom.checklist.domain.usecase.ObserveUnitsUseCase
import com.dataloom.checklist.domain.usecase.RemoveSectionUseCase
import com.dataloom.checklist.domain.usecase.SearchMasterItemsUseCase
import com.dataloom.checklist.domain.usecase.SetItemCompletedUseCase
import com.dataloom.checklist.domain.usecase.UpdateChecklistItemUseCase

/** Shared language packs: parsing them once keeps the table tests fast. */
val bundledPacks: LanguagePacks by lazy { LanguagePacks.bundled() }

/**
 * The whole AI layer wired by hand exactly as Hilt wires it, over the real seed catalog and an
 * in-memory checklist store. Only the repositories are fakes; every use case is the real one.
 */
class AiTestHarness(settings: AiSettings = AiSettings(enabled = true), serviceOverride: AIService? = null) {
    val catalog = SeedCatalogRepository()
    val checklists = InMemoryChecklistRepository(catalog)

    val lookup = CatalogLookup(SearchMasterItemsUseCase(catalog), ObserveCategoriesUseCase(catalog), ObserveUnitsUseCase(catalog))
    val grammar = CommandGrammar(bundledPacks)
    val parser = OfflineCommandParser(grammar, lookup)
    val validator = PlanValidator(lookup)
    val policy = ConfirmationPolicy()
    val observeDetail = ObserveChecklistDetailUseCase(checklists)
    val executor = ActionPlanExecutor(
        CreateChecklistUseCase(checklists),
        AddCategoriesToChecklistUseCase(checklists),
        AddMasterItemsToSectionUseCase(checklists, catalog),
        AddCustomItemUseCase(checklists, catalog),
        UpdateChecklistItemUseCase(checklists, catalog),
        SetItemCompletedUseCase(checklists),
        RemoveSectionUseCase(checklists),
        DeleteChecklistItemUseCase(checklists),
        observeDetail,
    )
    val readTools = ReadToolRunner(lookup, ObserveMasterItemsUseCase(catalog))
    val settingsSource = AiSettingsSource { settings }
    val assistant = AiAssistant(
        serviceOverride ?: parser,
        validator,
        policy,
        executor,
        settingsSource,
        observeDetail,
        ObserveUnitsUseCase(catalog),
    )

    /**
     * A "Weekly shopping" list with Groceries (Rice 1 kg, Milk ticked, a typed "Ghee") and Vegetables
     * (Onion). Returns its ID; setup writes are excluded from [InMemoryChecklistRepository.writes] checks
     * by reading the counter after this call.
     */
    suspend fun weeklyList(): ChecklistId {
        val groceries = catalog.category("groceries")
        val vegetables = catalog.category("vegetables")
        val id = checklists.createChecklist("Weekly shopping", null, listOf(groceries.id, vegetables.id))
        val sections = checklists.detailNow(id)!!.sections
        val groceriesSection = sections.first { it.category.id == groceries.id }.id
        val milkIds = checklists.addItems(
            groceriesSection,
            listOf(
                seeded("rice", Quantity.of(1), BuiltInUnits.KG.code),
                seeded("milk", null, null),
                NewChecklistItem(null, null, "Ghee", "en", null, null, notes = "private note"),
            ),
        )
        checklists.setItemCompleted(milkIds[1], true)
        checklists.addItems(sections.first { it.category.id == vegetables.id }.id, listOf(seeded("onion", null, null)))
        return id
    }

    private fun seeded(key: String, quantity: Quantity?, unit: UnitCode?): NewChecklistItem {
        val master = catalog.masterItem(key)
        return NewChecklistItem(master.id, master.canonicalKey, master.displayName, "en", quantity, unit, null)
    }

    suspend fun snapshot(id: ChecklistId?, locale: String = "en", focused: SectionId? = null): ContextSnapshot =
        assistant.snapshot(id, locale, focused)
}
