package com.dataloom.checklist.domain.usecase

import app.cash.turbine.test
import com.dataloom.checklist.domain.fake.FakeCatalogRepository
import com.dataloom.checklist.domain.model.BuiltInUnits
import com.dataloom.checklist.domain.model.Quantity
import com.dataloom.checklist.domain.model.UnitCode
import com.dataloom.checklist.domain.validation.ValidationError
import com.dataloom.checklist.domain.validation.ValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitUseCasesTest {

    private val catalog = FakeCatalogRepository()
    private val create = CreateCustomUnitUseCase(catalog)

    @Test
    fun `observe emits built-in units then new custom units`() = runTest {
        ObserveUnitsUseCase(catalog)().test {
            assertEquals(BuiltInUnits.all, awaitItem())
            create("bunch", allowsDecimal = false)
            assertEquals("bunch", awaitItem().last().customLabel)
        }
    }

    @Test
    fun `create trims the label and returns a custom code`() = runTest {
        val code = (create(" bunch ", allowsDecimal = false) as DomainResult.Success).value
        assertTrue(code.isCustom)
        val unit = catalog.getUnit(code)!!
        assertEquals("bunch", unit.customLabel)
        assertEquals(false, unit.allowsDecimal)
    }

    @Test
    fun `create rejects invalid and duplicate labels`() = runTest {
        create("bunch", allowsDecimal = false)
        assertEquals(DomainResult.Failure(DomainError.DuplicateName), create("BUNCH", allowsDecimal = true))
        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.UNIT_LABEL_BLANK))), create(" ", allowsDecimal = true))
        assertEquals(DomainResult.Failure(DomainError.Invalid(listOf(ValidationError.UNIT_LABEL_TOO_LONG))), create("u".repeat(21), allowsDecimal = true))
    }

    @Test
    fun `item validation resolves custom units and flags unknown codes`() = runTest {
        val code = (create("bunch", allowsDecimal = false) as DomainResult.Success).value
        val result = catalog.validateItem("Coriander", Quantity.parse("1.5"), code, null)
        assertEquals(listOf(ValidationError.QUANTITY_MUST_BE_WHOLE), (result as ValidationResult.Invalid).errors)
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.UNKNOWN_UNIT)),
            catalog.validateItem("Coriander", Quantity.of(1), UnitCode("CUSTOM_missing"), null),
        )
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.ITEM_NAME_BLANK, ValidationError.UNKNOWN_UNIT)),
            catalog.validateItem(" ", null, UnitCode("CUSTOM_missing"), null),
        )
    }
}
