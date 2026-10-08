package com.dataloom.checklist.presentation.category

import com.dataloom.checklist.domain.model.Category
import com.dataloom.checklist.domain.model.CategoryId
import com.dataloom.checklist.domain.usecase.CreateCategoryUseCase
import com.dataloom.checklist.domain.usecase.DomainResult
import com.dataloom.checklist.presentation.common.UiText
import com.dataloom.checklist.presentation.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One row of a category multi-select list. [icon] is an emoji (decorative). */
data class CategoryOptionUi(
    val id: CategoryId,
    val name: String,
    val icon: String,
    val selected: Boolean,
)

fun Category.toOption(selected: Boolean) = CategoryOptionUi(id, displayName, iconKey, selected)

/** The "Create category" dialog. */
data class NewCategoryDialogUi(
    val name: String = "",
    val error: UiText? = null,
    val isSaving: Boolean = false,
)

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
        mutableState.update { it?.copy(name = name, error = null) }
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
