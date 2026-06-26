package com.softyorch.stroopoverload

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            StroopTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StroopNavGraph()
                }
            }
        }
    }
}
