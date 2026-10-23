package app.fuelwise.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.model.Category
import app.fuelwise.core.model.SystemCategory
import app.fuelwise.core.testing.FakeCategoryRepository
import app.fuelwise.core.testing.MainDispatcherRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Expense categories (PRD F-04.2). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class CategoriesTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addRenameHideAndDelete() = runTest {
        val repo = FakeCategoryRepository(used = setOf("custom-1"))
        val vm = CategoriesViewModel(repo)
        vm.add("Driver salary", "person")
        vm.add("   ", "card")
        assertThat(repo.state.value.filter { !it.isSystem }.map { it.customName }).containsExactly("Driver salary")

        vm.rename(SystemCategory.Service.id, "Car service")
        assertThat(repo.state.value.first { it.id == SystemCategory.Service.id }.customName).isEqualTo("Car service")
        // Blank resets a preset to its own name.
        vm.rename(SystemCategory.Service.id, "")
        assertThat(repo.state.value.first { it.id == SystemCategory.Service.id }.customName).isNull()

        vm.setHidden(SystemCategory.Wash.id, true)
        assertThat(
            vm.state.first {
                !it.loading
            }.categories.first { it.id == SystemCategory.Wash.id }.isHidden,
        ).isTrue()

        // Used by expenses: hidden rather than deleted, and the screen says so.
        vm.remove("custom-1")
        assertThat(vm.events.first()).isEqualTo(CategoriesEvent.KeptBecauseUsed)
        assertThat(repo.state.value.first { it.id == "custom-1" }.isHidden).isTrue()
    }

    private val list = FakeCategoryRepository.SYSTEM.take(6).mapIndexed { i, c ->
        if (i == 2) c.copy(isHidden = true) else c
    } + Category("custom-1", null, "Driver salary", false, true, 6, "person")

    @Test fun categoriesLight() = capture("categories_light", false) {
        CategoriesContent(CategoriesUiState(loading = false, categories = list), CategoriesActions())
    }

    @Test fun categoriesDark() = capture("categories_dark", true) {
        CategoriesContent(CategoriesUiState(loading = false, categories = list), CategoriesActions())
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}
