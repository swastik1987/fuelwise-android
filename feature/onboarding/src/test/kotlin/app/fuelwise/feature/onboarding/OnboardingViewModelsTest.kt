package app.fuelwise.feature.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.fuelwise.core.model.Cities
import app.fuelwise.core.testing.FakeUserDataRepository
import app.fuelwise.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OnboardingViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val userData = FakeUserDataRepository()

    @Test
    fun searchingAndPickingACitySavesItAndClearsTheQuery() = runTest {
        val vm = HistoryViewModel(userData)
        vm.uiState.test {
            awaitItem()
            vm.onQueryChange("pun")
            assertThat(awaitItem().results.map { it.id }).containsExactly("pune")

            vm.onCitySelected(Cities.byId("pune")!!)
            val state = expectMostRecentItem()
            assertThat(state.selected?.id).isEqualTo("pune")
            assertThat(state.query).isEmpty()
        }
        assertThat(userData.userData.value.homeCityId).isEqualTo("pune")
    }

    @Test
    fun privacyDefaultsMatchFirstRunAndDoneSavesChoicesAndCompletesOnboarding() = runTest {
        val vm = PrivacyViewModel(userData)
        assertThat(vm.state.value.weeklyDigest).isFalse()
        assertThat(vm.state.value.monthlyRecap).isTrue()

        vm.setWeeklyDigest(true)
        vm.setMonthlyRecap(false)
        vm.finish()
        vm.done.first()

        val saved = userData.userData.value
        assertThat(saved.weeklyDigest).isTrue()
        assertThat(saved.monthlyRecap).isFalse()
        assertThat(saved.onboardingCompleted).isTrue()
    }
}
