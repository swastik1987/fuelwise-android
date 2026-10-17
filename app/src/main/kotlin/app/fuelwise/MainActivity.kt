package app.fuelwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.fuelwise.core.designsystem.theme.FuelwiseTheme
import app.fuelwise.navigation.FuelwiseApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FuelwiseTheme {
                FuelwiseApp()
            }
        }
    }
}
