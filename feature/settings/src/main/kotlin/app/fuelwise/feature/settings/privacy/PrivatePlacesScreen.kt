package app.fuelwise.feature.settings.privacy

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fuelwise.core.designsystem.component.FwOverline
import app.fuelwise.core.designsystem.component.FwPrimaryButton
import app.fuelwise.core.designsystem.component.FwSnackbarHost
import app.fuelwise.core.designsystem.component.FwTextField
import app.fuelwise.core.designsystem.component.FwTopBar
import app.fuelwise.core.designsystem.icon.FwIcons
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.PrivatePlace
import app.fuelwise.feature.settings.R

internal data class PrivatePlacesActions(
    val onBack: () -> Unit = {},
    val onAdd: (name: String) -> Unit = {},
    val onRemove: (id: String) -> Unit = {},
)

@Composable
internal fun PrivatePlacesScreen(onBack: () -> Unit, viewModel: PrivatePlacesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    // While-in-use location, asked only when the user marks a place.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val name = pendingName
        pendingName = null
        if (result.values.any { it } && name != null) viewModel.addHere(name)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    is PrivatePlacesEvent.Added -> resources.getString(R.string.private_places_added, event.name)
                    PrivatePlacesEvent.NoFix -> resources.getString(R.string.private_places_no_fix)
                },
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        PrivatePlacesContent(
            state,
            PrivatePlacesActions(
                onBack = onBack,
                onAdd = { name ->
                    val granted = listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                    if (granted) {
                        viewModel.addHere(name)
                    } else {
                        pendingName = name
                        permission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                onRemove = viewModel::remove,
            ),
        )
        FwSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Private places (PRD F-21.6): the list, and "add where I am now" with a name. */
@Composable
internal fun PrivatePlacesContent(state: PrivatePlacesUiState, actions: PrivatePlacesActions) {
    var name by rememberSaveable { mutableStateOf("") }
    val defaultName = stringResource(R.string.private_places_default_name)
    Column(Modifier.fillMaxSize().background(FwTheme.colors.bg)) {
        FwTopBar(
            title = stringResource(R.string.private_places_title),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.settings_back),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FwTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            Text(stringResource(R.string.private_places_body), style = FwTheme.type.body, color = FwTheme.colors.ink2)
            if (state.places.isEmpty() && !state.loading) {
                Text(
                    stringResource(R.string.private_places_empty),
                    style = FwTheme.type.bodyS,
                    color = FwTheme.colors.ink3,
                    modifier = Modifier.padding(top = FwTheme.spacing.s),
                )
            } else if (state.places.isNotEmpty()) {
                FwOverline(stringResource(R.string.private_places_list), Modifier.padding(top = FwTheme.spacing.s))
                state.places.forEach { PlaceRow(it, actions.onRemove) }
            }
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = FwTheme.spacing.gutter, vertical = FwTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FwTheme.spacing.s),
        ) {
            FwTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                label = stringResource(R.string.private_places_name),
                placeholder = stringResource(R.string.private_places_name_placeholder),
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            )
            FwPrimaryButton(
                stringResource(if (state.locating) R.string.private_places_locating else R.string.private_places_add),
                onClick = {
                    actions.onAdd(name.ifBlank { defaultName })
                    name = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.locating,
            )
        }
    }
}

@Composable
private fun PlaceRow(place: PrivatePlace, onRemove: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = FwTheme.size.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(FwIcons.Shield), null, tint = FwTheme.colors.ink2)
        Text(
            place.name,
            style = FwTheme.type.body,
            color = FwTheme.colors.ink,
            modifier = Modifier.weight(1f).padding(horizontal = FwTheme.spacing.s),
        )
        IconButton(onClick = { onRemove(place.id) }) {
            Icon(
                painterResource(FwIcons.Delete),
                stringResource(R.string.private_places_remove, place.name),
                tint = FwTheme.colors.ink2,
            )
        }
    }
}

private const val MAX_NAME = 40
