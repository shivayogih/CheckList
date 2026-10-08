package com.dataloom.checklist.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ActivityScenario
import com.dataloom.checklist.MainActivity
import com.dataloom.checklist.domain.repository.CatalogRepository
import com.dataloom.checklist.domain.repository.ChecklistRepository
import com.dataloom.checklist.domain.repository.ProfileRepository
import com.dataloom.checklist.domain.transfer.ApplyImportUseCase
import com.dataloom.checklist.domain.transfer.ExportChecklistsUseCase
import com.dataloom.checklist.domain.transfer.PreviewImportUseCase
import com.dataloom.checklist.localization.AppLanguageProvider
import com.dataloom.checklist.presentation.category.AddCategoriesViewModel
import com.dataloom.checklist.presentation.checklist.create.CreateChecklistViewModel
import com.dataloom.checklist.presentation.checklist.detail.ChecklistDetailViewModel
import com.dataloom.checklist.presentation.checklist.item.ItemEditorViewModel
import com.dataloom.checklist.presentation.home.HomeViewModel
import com.dataloom.checklist.presentation.masteritem.AddItemsViewModel
import com.dataloom.checklist.presentation.settings.profile.ProfileViewModel
import com.dataloom.checklist.transfer.DocumentAccess
import com.dataloom.checklist.transfer.FileSharer
import com.dataloom.checklist.transfer.TransferViewModel
import com.dataloom.checklist.transfer.pdf.ChecklistPdfWriter
import dagger.hilt.android.lifecycle.withCreationCallback
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The whole production Hilt graph builds and every entry point gets its dependencies (CL-172).
 *
 * Dagger already rejects a missing binding at compile time; this test also *runs* the providers,
 * which catches what compiles but fails on construction (a module that throws, a scope mistake, a
 * ViewModel whose assisted factory is wired wrong). Nothing is replaced: it uses the real modules.
 *
 * `AiAssistant` is not requested yet: injecting it exposed a dependency cycle in :ai's AiModule
 * (`Optional<AiSettingsSource>` is satisfied by `provideSettings` itself). That module belongs to
 * the AI track, which binds the Settings source; add `AiAssistant` here once it is fixed.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class AppGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var checklistRepository: ChecklistRepository

    @Inject lateinit var catalogRepository: CatalogRepository

    @Inject lateinit var profileRepository: ProfileRepository

    @Inject lateinit var languageProvider: AppLanguageProvider

    @Inject lateinit var exportChecklists: ExportChecklistsUseCase

    @Inject lateinit var previewImport: PreviewImportUseCase

    @Inject lateinit var applyImport: ApplyImportUseCase

    @Inject lateinit var documentAccess: DocumentAccess

    @Inject lateinit var fileSharer: FileSharer

    @Inject lateinit var pdfWriter: ChecklistPdfWriter

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject lateinit var otherChecklistRepository: ChecklistRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `singleton graph provides repositories and transfer services`() {
        listOf(
            catalogRepository, profileRepository, languageProvider, exportChecklists, previewImport, applyImport,
            documentAccess, fileSharer, pdfWriter, applicationScope,
        ).forEach(::assertNotNull)
        // One database and one repository per process (Room invalidation needs a single instance).
        assertSame(checklistRepository, otherChecklistRepository)
    }

    @Test
    fun `main activity and every screen's view model are created by Hilt`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val provider = ViewModelProvider(activity)
                listOf(
                    HomeViewModel::class.java,
                    CreateChecklistViewModel::class.java,
                    ProfileViewModel::class.java,
                    TransferViewModel::class.java,
                ).forEach { assertNotNull(provider[it]) }

                val extras = activity.defaultViewModelCreationExtras
                fun <VM : ViewModel> assisted(type: KClass<VM>, extras: CreationExtras): VM =
                    ViewModelProvider.create(activity, activity.defaultViewModelProviderFactory, extras)[type]

                assertNotNull(
                    assisted(
                        ChecklistDetailViewModel::class,
                        extras.withCreationCallback<ChecklistDetailViewModel.Factory> { it.create(MISSING_ID) },
                    ),
                )
                assertNotNull(
                    assisted(
                        AddCategoriesViewModel::class,
                        extras.withCreationCallback<AddCategoriesViewModel.Factory> { it.create(MISSING_ID) },
                    ),
                )
                assertNotNull(
                    assisted(
                        AddItemsViewModel::class,
                        extras.withCreationCallback<AddItemsViewModel.Factory> { it.create(MISSING_ID, MISSING_ID) },
                    ),
                )
                assertNotNull(
                    assisted(
                        ItemEditorViewModel::class,
                        extras.withCreationCallback<ItemEditorViewModel.Factory> {
                            it.create(MISSING_ID, MISSING_ID, null, "")
                        },
                    ),
                )
            }
        }
    }

    private companion object {
        const val MISSING_ID = "graph-test-missing"
    }
}
