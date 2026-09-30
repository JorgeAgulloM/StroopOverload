package com.softyorch.stroopoverload

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.softyorch.stroopoverload.ui.StroopNavGraph
import com.softyorch.stroopoverload.ui.theme.StroopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark. The default (auto) style follows the system theme, so a
        // light system theme drew dark status/navigation bar icons on the dark UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            StroopTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StroopNavGraph()
                }
            }
        }
    }
}
