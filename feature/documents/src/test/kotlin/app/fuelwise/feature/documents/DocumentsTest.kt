package app.fuelwise.feature.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.core.model.Document
import app.fuelwise.core.model.DocumentType
import app.fuelwise.core.model.InsuranceDetails
import app.fuelwise.core.model.Money
import app.fuelwise.core.model.PolicyType
import app.fuelwise.core.testing.SampleVehicles
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots of design.md screen 24: vault, quick view and the add flow. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class DocumentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private val now = date(2026, 10, 16)

    private val insurance = Document(
        "d1",
        "safari",
        DocumentType.Insurance,
        number = "OG-27-1901-1801-00012345",
        expiryDateUtcMs = date(2027, 3, 14),
        insurance = InsuranceDetails("ICICI Lombard", PolicyType.Comprehensive, Money(125_000_000), 35),
    )
    private val docs = listOf(
        insurance,
        Document("d2", "safari", DocumentType.Puc, number = "KA01-PUC-882231", expiryDateUtcMs = date(2026, 11, 2)),
        Document("d3", null, DocumentType.Licence, number = "KA01 20190012345", expiryDateUtcMs = date(2026, 9, 30)),
        Document("d4", "safari", DocumentType.Rc, number = "KA 01 AB 1234"),
    )
    private val list = DocumentsUiState(false, docs, mapOf("safari" to SampleVehicles.safari), now)

    @Test fun vaultLight() = capture("documents_light", false) { DocumentsContent(list, {}, {}, {}) }

    @Test fun vaultDark() = capture("documents_dark", true) { DocumentsContent(list, {}, {}, {}) }

    @Test fun quickViewLight() = capture("document_view_light", false) {
        DocumentViewContent(DocumentViewState(insurance, SampleVehicles.safari, now), emptyList(), {}, {}, {})
    }

    @Test fun editorDark() = capture("document_editor_dark", true) {
        DocumentEditorContent(
            state = DocumentForm(
                loading = false,
                vehicles = listOf(SampleVehicles.safari),
                vehicleId = "safari",
                type = DocumentType.Insurance,
                number = "OG-27-1901",
                insurer = "ICICI Lombard",
                expiryDateUtcMs = date(2027, 3, 14),
                policyType = PolicyType.Comprehensive,
                nowUtcMs = now,
            ),
            attachments = emptyList(),
            onUpdate = {},
            onAddPhoto = {},
            onAddPdf = {},
            onRemove = {},
            onSave = {},
            onClose = {},
            onDelete = {},
        )
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { FuelwiseTheme(darkTheme = dark) { content() } }
        compose.onNode(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
    }
}
