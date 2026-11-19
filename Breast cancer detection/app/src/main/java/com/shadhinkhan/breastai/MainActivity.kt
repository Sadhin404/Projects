package com.shadhinkhan.breastai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shadhinkhan.breastai.ui.AppNav
import com.shadhinkhan.breastai.ui.MainViewModel
import com.shadhinkhan.breastai.ui.theme.BreastAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        setContent {
            val vm: MainViewModel = viewModel()
            BreastAITheme(darkTheme = vm.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
    }

    /** Run at the display's highest refresh rate at the current resolution:
     *  120 Hz panels get 120 FPS, 60 Hz panels stay at 60 — adaptive. */
    private fun requestHighestRefreshRate() {
        try {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
            if (best != null) {
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                window.attributes = lp
            }
        } catch (_: Exception) {
            // Single-mode devices need nothing.
        }
    }
}
