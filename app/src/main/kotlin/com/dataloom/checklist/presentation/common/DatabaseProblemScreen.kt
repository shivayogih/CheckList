package com.dataloom.checklist.presentation.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dataloom.checklist.R
import com.dataloom.checklist.data.local.database.DatabaseFailure
import com.dataloom.checklist.presentation.components.EmptyState
import com.dataloom.checklist.presentation.components.PrimaryButton

/**
 * Shown instead of the app when the database could not be opened (CL-321, docs/db-migrations.md). It says
 * what happened in plain words and that nothing was lost; the only action closes the app, because the next
 * launch tries the open again (a failure is remembered for the rest of this process).
 */
@Composable
fun DatabaseProblemScreen(failure: DatabaseFailure, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val (emoji, title, body) = when (failure) {
        is DatabaseFailure.MigrationFailed -> Triple(
            "🛟",
            R.string.db_migration_failed_title,
            R.string.db_migration_failed_body,
        )
        is DatabaseFailure.Downgrade -> Triple("⬆️", R.string.db_newer_title, R.string.db_newer_body)
        DatabaseFailure.OpenFailed -> Triple("⚠️", R.string.db_open_failed_title, R.string.db_open_failed_body)
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        EmptyState(
            emoji = emoji,
            title = stringResource(title),
            body = stringResource(body),
            modifier = Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding(),
        ) {
            PrimaryButton(
                text = stringResource(R.string.db_problem_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
