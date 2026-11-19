package com.shadhinkhan.breastai.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shadhinkhan.breastai.core.Haptics
import com.shadhinkhan.breastai.data.HistoryEntry
import com.shadhinkhan.breastai.data.HistoryStore
import com.shadhinkhan.breastai.engine.AnalysisResult
import com.shadhinkhan.breastai.engine.InferenceEngine
import com.shadhinkhan.breastai.engine.NotUltrasoundException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Analyzing(val stage: Int) : AnalysisState
    data object NotScan : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

val STAGE_LABELS = listOf(
    "Checking the image…",
    "Running the AI models…",
    "Building the heatmap…",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("breast_ai", Context.MODE_PRIVATE)

    // Dark mode is the default (his order) until he switches it off himself.
    var darkTheme by mutableStateOf(prefs.getBoolean("dark_theme", true))
        private set
    var seenOnboarding by mutableStateOf(prefs.getBoolean("seen_onboarding", false))
        private set

    var pickedBytes by mutableStateOf<ByteArray?>(null)
        private set
    var pickedName by mutableStateOf("")
        private set
    var pickedPreview by mutableStateOf<Bitmap?>(null)
        private set

    var analysisState by mutableStateOf<AnalysisState>(AnalysisState.Idle)
        private set
    var currentResult by mutableStateOf<AnalysisResult?>(null)
        private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    init {
        viewModelScope.launch { InferenceEngine.warmUp(app) }
        refreshHistory()
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        prefs.edit().putBoolean("dark_theme", darkTheme).apply()
    }

    fun completeOnboarding() {
        seenOnboarding = true
        prefs.edit().putBoolean("seen_onboarding", true).apply()
    }

    fun onImagePicked(bytes: ByteArray, name: String) {
        pickedBytes = bytes
        pickedName = name
        analysisState = AnalysisState.Idle
        pickedPreview = try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null && (bmp.width > 512 || bmp.height > 512)) {
                val s = 512f / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * s).toInt().coerceAtLeast(1),
                    (bmp.height * s).toInt().coerceAtLeast(1), true
                )
            } else {
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearPicked() {
        pickedBytes = null
        pickedName = ""
        pickedPreview = null
        analysisState = AnalysisState.Idle
    }

    fun analyze(onSuccess: () -> Unit) {
        val bytes = pickedBytes ?: return
        val app = getApplication<Application>()
        var ticker: Job? = null
        analysisState = AnalysisState.Analyzing(0)
        viewModelScope.launch {
            var stage = 0
            ticker = launch {
                while (isActive) {
                    delay(1100)
                    stage = minOf(stage + 1, STAGE_LABELS.lastIndex)
                    val s = analysisState
                    if (s is AnalysisState.Analyzing) {
                        analysisState = AnalysisState.Analyzing(stage)
                    }
                }
            }
            try {
                val result = InferenceEngine.analyze(app, bytes)
                currentResult = result
                HistoryStore.add(app, result)
                refreshHistory()
                Haptics.success(app)
                analysisState = AnalysisState.Idle
                onSuccess()
            } catch (_: NotUltrasoundException) {
                Haptics.fail(app)
                analysisState = AnalysisState.NotScan
            } catch (e: Exception) {
                Haptics.fail(app)
                analysisState = AnalysisState.Failed(
                    "Analysis failed: ${e.message ?: "unknown error"}"
                )
            } finally {
                ticker?.cancel()
            }
        }
    }

    fun openHistoryEntry(entry: HistoryEntry, onOpen: () -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = HistoryStore.loadResult(app, entry)
            if (result != null) {
                currentResult = result
                onOpen()
            }
        }
    }

    fun deleteHistoryEntry(entry: HistoryEntry) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            HistoryStore.delete(app, entry.id)
            refreshHistory()
        }
    }

    private fun refreshHistory() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            history = HistoryStore.loadEntries(app)
        }
    }
}
