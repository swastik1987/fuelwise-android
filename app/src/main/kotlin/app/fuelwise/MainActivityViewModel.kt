package app.fuelwise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.ThemeMode
import app.fuelwise.core.data.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Ready(val onboardingCompleted: Boolean, val themeMode: ThemeMode) : MainActivityUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(userData: UserDataRepository) : ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = userData.userData
        .map { MainActivityUiState.Ready(it.onboardingCompleted, it.themeMode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainActivityUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
