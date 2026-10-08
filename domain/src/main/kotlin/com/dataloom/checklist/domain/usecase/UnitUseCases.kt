package com.dataloom.checklist.domain.usecase

import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.model.UnitDef
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.validation.UnitValidator
import com.dataloom.checklist.domain.validation.ValidationResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveUnitsUseCase @Inject constructor(private val catalog: CatalogRepository) {
    operator fun invoke(): Flow<List<UnitDef>> = catalog.observeUnits()
}

/**
 * Creates a user unit ("bunch", "packet"). Labels must be unique among custom units, ignoring case.
 * Built-in labels are localized string resources the domain cannot see, so a custom "kg" is allowed.
 */
class CreateCustomUnitUseCase @Inject constructor(private val catalog: CatalogRepository) {
    suspend operator fun invoke(label: String, allowsDecimal: Boolean): DomainResult<UnitCode> {
        val cleanLabel = when (val result = UnitValidator.validateLabel(label)) {
            is ValidationResult.Invalid -> return result.toFailure()
            is ValidationResult.Valid -> result.value
        }
        val taken = catalog.observeUnits().first().any { it.customLabel?.equals(cleanLabel, ignoreCase = true) == true }
        if (taken) return failure(DomainError.DuplicateName)
        return success(catalog.createCustomUnit(cleanLabel, allowsDecimal))
    }
}
