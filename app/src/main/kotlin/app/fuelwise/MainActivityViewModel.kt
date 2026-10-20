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

    data class Ready(
        val onboardingCompleted: Boolean,
        val themeMode: ThemeMode,
        val dynamicColor: Boolean = false,
        val appLock: Boolean = false,
        val appLockTimeoutMinutes: Int = 0,
    ) : MainActivityUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(userData: UserDataRepository) : ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = userData.userData
        .map {
            MainActivityUiState.Ready(
                onboardingCompleted = it.onboardingCompleted,
                themeMode = it.themeMode,
                dynamicColor = it.dynamicColor,
                appLock = it.appLock,
                appLockTimeoutMinutes = it.appLockTimeoutMinutes,
            )
        }
        // Eagerly: the lock check in onStart reads the latest value even before the UI collects it.
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainActivityUiState.Loading)
}
