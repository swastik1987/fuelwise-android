package app.fuelwise.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fuelwise.core.data.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Privacy choices (design.md screen 4). The crash-report toggle is left out: R1 ships with no crash reporting
 * (PRD D7). Choices are saved together with "onboarding done" when the user taps Done.
 */
data class PrivacyUiState(
    val weeklyDigest: Boolean = false,
    val monthlyRecap: Boolean = true,
    val saving: Boolean = false,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(private val userData: UserDataRepository) : ViewModel() {
    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)
    val done = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = userData.userData.first()
            _state.update { it.copy(weeklyDigest = saved.weeklyDigest, monthlyRecap = saved.monthlyRecap) }
        }
    }

    fun setWeeklyDigest(on: Boolean) = _state.update { it.copy(weeklyDigest = on) }

    fun setMonthlyRecap(on: Boolean) = _state.update { it.copy(monthlyRecap = on) }

    fun finish() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val s = _state.value
            userData.setDigestChoices(weeklyDigest = s.weeklyDigest, monthlyRecap = s.monthlyRecap)
            userData.setOnboardingCompleted(true)
            doneChannel.send(Unit)
        }
    }
}
