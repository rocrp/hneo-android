package dev.rocry.hneo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.rocry.hneo.ui.navigation.HneoNavGraph
import dev.rocry.hneo.ui.theme.HneoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HneoTheme {
                HneoNavGraph()
            }
        }
    }
}
