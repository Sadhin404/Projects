package com.shadhinkhan.breastai.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class NotUltrasoundException : Exception()

private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
private const val INPUT = 224
private const val DISPLAY = 448

private val MODEL_FILES = listOf(
    "models/model_b3_s42.onnx",
    "models/model_b3_s7.onnx",
    "models/model_b3_s123.onnx",
)
private val MODEL_TAGS = listOf("b3_s42", "b3_s7", "b3_s123")

/**
 * On-device inference with the OFFICIAL onnxruntime-android library —
 * no plugin layer. Same ultrasound gate, same preprocessing, same three
 * EfficientNet-B3 models (pure fp32), same averaged analytic Grad-CAM
 * as the project website.
 */
object InferenceEngine {

    private val loadMutex = Mutex()
    private val running = AtomicBoolean(false)
    @Volatile private var sessions: List<OrtSession>? = null
    @Volatile private var camWeights: Array<Array<FloatArray>>? = null

    val isLoaded: Boolean get() = sessions != null

    suspend fun ensureLoaded(context: Context) {
        if (sessions != null) return
        loadMutex.withLock {
            if (sessions != null) return
            withContext(Dispatchers.IO) {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    // CPU is the default execution provider — nothing else
                    // is added, the most battle-tested path there is.
                }
                sessions = MODEL_FILES.map { path ->
                    val bytes = context.assets.open(path).use { it.readBytes() }
                    env.createSession(bytes, opts)
                }
                val json = context.assets.open("models/cam_weights_ensemble.json")
                    .use { it.readBytes().toString(Charsets.UTF_8) }
                val models = JSONObject(json).getJSONObject("models")
                camWeights = Array(MODEL_TAGS.size) { m ->
                    val perClass = models.getJSONArray(MODEL_TAGS[m])
                    Array(perClass.length()) { c ->
                        val arr = perClass.getJSONArray(c)
                        FloatArray(arr.length()) { k -> arr.getDouble(k).toFloat() }
                    }
                }
            }
        }
    }

    /** Fire-and-forget warm-up: never throws. */
    suspend fun warmUp(context: Context) {
        try {
            ensureLoaded(context)
        } catch (_: Exception) {
            // analyze() surfaces the error when the user actually runs it.
        }
    }

    suspend fun analyze(context: Context, fileBytes: ByteArray): AnalysisResult {
        check(running.compareAndSet(false, true)) { "An analysis is already running." }
        try {
            ensureLoaded(context)
            return withContext(Dispatchers.Default) { analyzeBlocking(context, fileBytes) }
        } finally {
            running.set(false)
        }
    }

    private fun analyzeBlocking(context: Context, fileBytes: ByteArray): AnalysisResult {
        val decoded = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
            ?: throw NotUltrasoundException()
        if (!checkUltrasound(decoded)) throw NotUltrasoundException()

        val env = OrtEnvironment.getEnvironment()
        val loadedSessions = sessions ?: error("Models failed to load.")
        val weights = camWeights ?: error("CAM weights failed to load.")

        val tensor = buildTensor(decoded)
        val display = Bitmap.createScaledBitmap(decoded, DISPLAY, DISPLAY, true)
        val displayPx = IntArray(DISPLAY * DISPLAY)
        display.getPixels(displayPx, 0, DISPLAY, 0, 0, DISPLAY, DISPLAY)

        val probs = DoubleArray(3)
        val memberActs = ArrayList<FloatArray>(loadedSessions.size)
        for (session in loadedSessions) {
            val input = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(tensor), longArrayOf(1, 3, INPUT.toLong(), INPUT.toLong())
            )
            try {
                session.run(mapOf("input" to input)).use { result ->
                    val logitsT = result.get("logits").orElseThrow() as OnnxTensor
                    val actsT = result.get("acts").orElseThrow() as OnnxTensor
                    val logits = FloatArray(3)
                    logitsT.floatBuffer.duplicate().apply { rewind() }.get(logits)
                    val acts = FloatArray(1536 * 49)
                    actsT.floatBuffer.duplicate().apply { rewind() }.get(acts)
                    memberActs.add(acts)
                    val mx = logits.max()
                    var sum = 0.0
                    val exps = DoubleArray(3) { c ->
                        val e = exp((logits[c] - mx).toDouble())
                        sum += e
                        e
                    }
                    for (c in 0..2) probs[c] += (exps[c] / sum) / loadedSessions.size
                }
            } finally {
                input.close()
            }
        }

        var pred = 0
        for (c in 1..2) if (probs[c] > probs[pred]) pred = c

        val classWeights = Array(loadedSessions.size) { m -> weights[m][pred] }
        val rendered = GradCam.render(memberActs, classWeights, displayPx)

        return AnalysisResult(
            id = System.currentTimeMillis().toString(),
            createdAt = System.currentTimeMillis(),
            predictedIndex = pred,
            probabilities = probs,
            bbox = rendered.bbox,
            original = display,
            heatmap = rendered.heatmap,
            overlay = rendered.overlay,
        )
    }

    private fun buildTensor(src: Bitmap): FloatArray {
        val small = Bitmap.createScaledBitmap(src, INPUT, INPUT, true)
        val px = IntArray(INPUT * INPUT)
        small.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT)
        val n = INPUT * INPUT
        val tensor = FloatArray(3 * n)
        for (i in 0 until n) {
            tensor[i] = (Color.red(px[i]) / 255f - MEAN[0]) / STD[0]
            tensor[n + i] = (Color.green(px[i]) / 255f - MEAN[1]) / STD[1]
            tensor[2 * n + i] = (Color.blue(px[i]) / 255f - MEAN[2]) / STD[2]
        }
        return tensor
    }

    /**
     * Port of the website's checkUltrasoundImage(): real BUSI scans are
     * pure greyscale with speckle texture; colour photos, blank images,
     * smooth graphics and flat screenshots fail these checks.
     */
    internal fun checkUltrasound(src: Bitmap): Boolean {
        val maxDim = max(src.width, src.height)
        val scale = min(1.0, 256.0 / maxDim)
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        val im = if (w == src.width && h == src.height) src
            else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        im.getPixels(px, 0, w, 0, 0, w, h)
        val n = w * h
        val lum = DoubleArray(n)
        var color = 0
        var satSum = 0.0
        var bright = 0
        var lumSum = 0.0
        for (i in 0 until n) {
            val r = Color.red(px[i])
            val g = Color.green(px[i])
            val b = Color.blue(px[i])
            val sat = max(r, max(g, b)) - min(r, min(g, b))
            if (sat > 32) color++
            satSum += sat
            val l = 0.299 * r + 0.587 * g + 0.114 * b
            lum[i] = l
            lumSum += l
            if (l > 200) bright++
        }
        val mean = lumSum / n
        var varSum = 0.0
        var spkSum = 0.0
        var flat = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val d = lum[i] - mean
                varSum += d * d
                if (x + 1 < w) {
                    spkSum += abs(lum[i + 1] - lum[i])
                    if (lum[i + 1] == lum[i]) flat++
                }
            }
        }
        val lumStd = sqrt(varSum / n)
        val speckle = spkSum / (n - h)
        val colorFrac = color.toDouble() / n
        val meanSat = satSum / n
        val brightFrac = bright.toDouble() / n
        val flatFrac = flat.toDouble() / (n - h)
        if (colorFrac > 0.05 || meanSat > 14) return false
        if (brightFrac > 0.35) return false
        if (speckle < 1.2 || lumStd < 12) return false
        if (flatFrac > 0.60) return false
        return true
    }
}
