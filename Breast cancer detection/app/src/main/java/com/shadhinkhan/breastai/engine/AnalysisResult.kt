package com.shadhinkhan.breastai.engine

import android.graphics.Bitmap
import com.shadhinkhan.breastai.core.CLASS_DISPLAY
import com.shadhinkhan.breastai.core.CLASS_KEYS

/** Result of one analysis. Images are display-size (448x448) bitmaps. */
class AnalysisResult(
    val id: String,
    val createdAt: Long,
    val predictedIndex: Int,
    val probabilities: DoubleArray, // [normal, benign, malignant]
    val bbox: FloatArray, // [x, y, w, h] normalised 0..1
    val original: Bitmap,
    val heatmap: Bitmap,
    val overlay: Bitmap,
) {
    val predictedKey: String get() = CLASS_KEYS[predictedIndex]
    val predictedDisplay: String get() = CLASS_DISPLAY[predictedIndex]
    val confidence: Double get() = probabilities[predictedIndex]
}
