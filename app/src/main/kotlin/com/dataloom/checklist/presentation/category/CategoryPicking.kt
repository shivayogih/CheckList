package com.dataloom.checklist.presentation.category

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.usecase.CreateCategoryUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.domain.validation.CategoryValidator
import com.dataloom.checklist.domain.validation.FieldLimits
import com.dataloom.checklist.domain.validation.InputText
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.isAccepted
import com.dataloom.checklist.presentation.common.liveError
import com.dataloom.checklist.presentation.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One row of a category multi-select list. [icon] is an emoji (decorative). [frequent] marks a
 * category the user has put in a checklist before, shown first under "Used often".
 */
data class CategoryOptionUi(
    val id: CategoryId,
    val name: String,
    val icon: String,
    val selected: Boolean,
    val frequent: Boolean = false,
)

fun Category.toOption(selected: Boolean) =
    CategoryOptionUi(id, displayName, iconKey, selected, frequent = usageCount > 0)

/** The "Create category" dialog. */
data class NewCategoryDialogUi(
    val name: String = "",
    val error: UiText? = null,
    val isSaving: Boolean = false,
) {
    /** Create is enabled only for a name the domain would accept (CL-280). */
    val canConfirm: Boolean get() = !isSaving && isAccepted(name, CategoryValidator::validateName)
}

/**
 * Drives the "Create category" dialog for every screen that picks categories (create checklist,
 * add categories). Validation and the duplicate-name rule live in [CreateCategoryUseCase].
 */
class NewCategoryDialogController(
    private val createCategory: CreateCategoryUseCase,
    private val language: () -> String,
) {
    private val mutableState = MutableStateFlow<NewCategoryDialogUi?>(null)
    val state: StateFlow<NewCategoryDialogUi?> = mutableState.asStateFlow()

    fun open() {
        mutableState.value = NewCategoryDialogUi()
    }

    fun dismiss() {
        mutableState.value = null
    }

    fun onNameChanged(name: String) {
        val clean = InputText.forField(name, FieldLimits.CATEGORY_NAME_MAX)
        mutableState.update { it?.copy(name = clean, error = liveError(clean, CategoryValidator::validateName)) }
    }

    /** Returns the new category's ID and closes the dialog, or keeps it open with an error. */
    suspend fun confirm(): CategoryId? {
        val current = mutableState.value ?: return null
        if (current.isSaving) return null
        mutableState.value = current.copy(isSaving = true, error = null)
        return when (val result = createCategory(current.name, CUSTOM_CATEGORY_ICON, language())) {
            is DomainResult.Success -> {
                mutableState.value = null
                result.value
            }
            is DomainResult.Failure -> {
                mutableState.value = current.copy(isSaving = false, error = result.error.toUiText())
                null
            }
        }
    }

    companion object {
        /** Icon for user-created categories; seeded ones bring their own emoji. */
        const val CUSTOM_CATEGORY_ICON = "📌"
    }
}
