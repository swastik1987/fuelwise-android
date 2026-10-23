package app.fuelwise.feature.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSettingsRow
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwSwitchRow
import app.fuelwise.core.designsystem.component.FwTextButton
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.model.Category
import app.fuelwise.core.ui.entry.EntryLabels
import app.fuelwise.core.ui.entry.categoryName

internal data class CategoriesActions(
    val onBack: () -> Unit = {},
    val onAdd: (name: String, icon: String) -> Unit = { _, _ -> },
    val onRename: (id: String, name: String) -> Unit = { _, _ -> },
    val onHidden: (id: String, hidden: Boolean) -> Unit = { _, _ -> },
    val onRemove: (id: String) -> Unit = {},
)

@Composable
internal fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                resources.getString(
                    when (event) {
                        CategoriesEvent.Deleted -> R.string.categories_deleted
                        CategoriesEvent.KeptBecauseUsed -> R.string.categories_hidden_used
                    },
                ),
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        CategoriesContent(
            state,
            CategoriesActions(onBack, viewModel::add, viewModel::rename, viewModel::setHidden, viewModel::remove),
        )
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Which sheet is open: a category's id, [ADD] for a new one, or none. */
private const val ADD = "+"

/** Expense categories (PRD F-04.2): presets and the user's own, hidden ones marked. */
@Composable
internal fun CategoriesContent(state: CategoriesUiState, actions: CategoriesActions) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.categories_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.expense_back),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = FwTheme.spacing.gutter)) {
            items(state.categories, key = { it.id }) { category ->
                FwSettingsRow(
                    categoryName(category),
                    value = if (category.isHidden) stringResource(R.string.categories_hidden) else null,
                    icon = EntryLabels.categoryIcon(category),
                    titleColor = if (category.isHidden) FwTheme.colors.ink3 else FwTheme.colors.ink,
                    onClick = { editing = category.id },
                )
            }
        }
        FwPrimaryButton(
            stringResource(R.string.categories_add),
            { editing = ADD },
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
        )
    }
    val id = editing ?: return
    CategorySheet(
        category = state.categories.firstOrNull { it.id == id },
        actions = actions,
        onDismiss = { editing = null },
    )
}

/** Add (no [category]) or edit one: name, icon for custom ones, shown or hidden, delete. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(category: Category?, actions: CategoriesActions, onDismiss: () -> Unit) {
    val displayName = category?.let { categoryName(it) }.orEmpty()
    var name by rememberSaveable(category?.id) { mutableStateOf(displayName) }
    var icon by rememberSaveable(category?.id) { mutableStateOf(category?.icon ?: EntryLabels.CUSTOM_ICONS.first().first) }
    val custom = category == null || !category.isSystem
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FwTheme.colors.surface,
        shape = FwTheme.radius.sheet,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            Text(
                stringResource(if (category == null) R.string.categories_add else R.string.categories_edit),
                style = FwTheme.type.h3,
                color = FwTheme.colors.ink,
            )
            FwTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = stringResource(R.string.categories_name),
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            )
            if (category?.isSystem == true) {
                Text(stringResource(R.string.categories_reset_hint), style = FwTheme.type.caption, color = FwTheme.colors.ink2)
            }
            if (custom) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.xs),
                ) {
                    EntryLabels.CUSTOM_ICONS.forEach { (key, res) -> IconChoice(res, key == icon) { icon = key } }
                }
            }
            if (category != null) {
                FwSwitchRow(
                    title = stringResource(R.string.categories_show),
                    subtitle = stringResource(R.string.categories_show_body),
                    checked = !category.isHidden,
                    onCheckedChange = { actions.onHidden(category.id, !it) },
                )
            }
            FwPrimaryButton(
                stringResource(R.string.categories_save),
                onClick = {
                    when {
                        category == null -> actions.onAdd(name, icon)
                        // Unchanged: don't freeze a preset's translated name as a custom one.
                        name != displayName -> actions.onRename(category.id, name)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = category?.isSystem == true || name.isNotBlank(),
            )
            if (category != null && custom) {
                FwTextButton(
                    stringResource(R.string.categories_delete),
                    {
                        actions.onRemove(category.id)
                        onDismiss()
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun IconChoice(icon: Int, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.categories_icon)
    Surface(
        selected = selected,
        onClick = onClick,
        shape = FwTheme.radius.tile,
        color = FwTheme.colors.surface2,
        border = if (selected) BorderStroke(FwTheme.size.hairline * 2, FwTheme.colors.ink) else null,
        modifier = Modifier.size(FwTheme.size.minTouchTarget).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = FwTheme.colors.ink, modifier = Modifier.size(FwTheme.size.icon))
        }
    }
}

private const val MAX_NAME = 30
