package com.kalpkari.watermark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kalpkari.watermark.ui.KalpkariTheme
import com.kalpkari.watermark.ui.WatermarkScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KalpkariTheme {
                Surface(Modifier.fillMaxSize()) {
                    WatermarkScreen()
                }
            }
        }
    }
}
