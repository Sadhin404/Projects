package com.shadhinkhan.breastai.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DISPLAY = 448
private const val INPUT = 224

/** Analytic Grad-CAM + focus box + heatmap/overlay rendering — a faithful
 *  port of the website/Flutter math. */
object GradCam {

    class Rendered(
        val bbox: FloatArray, // [x, y, w, h] normalised 0..1
        val heatmap: Bitmap,
        val overlay: Bitmap,
    )

    fun render(
        memberActs: List<FloatArray>, // per member: 1536*7*7
        classWeights: Array<FloatArray>, // per member: 1536 weights for the predicted class
        displayPx: IntArray, // DISPLAY x DISPLAY ARGB
    ): Rendered {
        // Averaged analytic Grad-CAM at 7x7.
        val cam7 = FloatArray(49)
        for (m in memberActs.indices) {
            val w = classWeights[m]
            val acts = memberActs[m]
            for (k in 0 until 1536) {
                val wk = w[k]
                if (wk == 0f) continue
                val off = k * 49
                for (j in 0 until 49) {
                    cam7[j] += wk * acts[off + j]
                }
            }
        }
        var mn = Float.POSITIVE_INFINITY
        var mxv = Float.NEGATIVE_INFINITY
        for (j in 0 until 49) {
            cam7[j] = max(0f, cam7[j])
            if (cam7[j] < mn) mn = cam7[j]
            if (cam7[j] > mxv) mxv = cam7[j]
        }
        for (j in 0 until 49) {
            cam7[j] = (cam7[j] - mn) / (mxv - mn + 1e-8f)
        }

        val cam224 = upsampleBilinear(cam7, 7, INPUT)
        val bbox = focusBox(cam224, INPUT, INPUT)
        val camBig = upsampleBilinear(cam7, 7, DISPLAY)

        val total = DISPLAY * DISPLAY
        val heatPx = IntArray(total)
        val overPx = IntArray(total)
        for (i in 0 until total) {
            val r = Color.red(displayPx[i])
            val g = Color.green(displayPx[i])
            val b = Color.blue(displayPx[i])
            val jet = jetColor(camBig[i].toDouble())
            heatPx[i] = Color.rgb(jet[0], jet[1], jet[2])
            overPx[i] = Color.rgb(
                (0.55 * r + 0.45 * jet[0]).roundToInt(),
                (0.55 * g + 0.45 * jet[1]).roundToInt(),
                (0.55 * b + 0.45 * jet[2]).roundToInt(),
            )
        }
        val heatmap = Bitmap.createBitmap(DISPLAY, DISPLAY, Bitmap.Config.ARGB_8888)
        heatmap.setPixels(heatPx, 0, DISPLAY, 0, 0, DISPLAY, DISPLAY)
        val overlay = Bitmap.createBitmap(DISPLAY, DISPLAY, Bitmap.Config.ARGB_8888)
        overlay.setPixels(overPx, 0, DISPLAY, 0, 0, DISPLAY, DISPLAY)
        return Rendered(bbox, heatmap, overlay)
    }

    private fun upsampleBilinear(src: FloatArray, srcSize: Int, dstSize: Int): FloatArray {
        val out = FloatArray(dstSize * dstSize)
        val scale = srcSize.toDouble() / dstSize
        for (y in 0 until dstSize) {
            var sy = (y + 0.5) * scale - 0.5
            sy = sy.coerceIn(0.0, (srcSize - 1).toDouble())
            val y0 = floor(sy).toInt()
            val y1 = min(y0 + 1, srcSize - 1)
            val fy = sy - y0
            for (x in 0 until dstSize) {
                var sx = (x + 0.5) * scale - 0.5
                sx = sx.coerceIn(0.0, (srcSize - 1).toDouble())
                val x0 = floor(sx).toInt()
                val x1 = min(x0 + 1, srcSize - 1)
                val fx = sx - x0
                val v00 = src[y0 * srcSize + x0]
                val v01 = src[y0 * srcSize + x1]
                val v10 = src[y1 * srcSize + x0]
                val v11 = src[y1 * srcSize + x1]
                out[y * dstSize + x] = (
                    (v00 * (1 - fx) + v01 * fx) * (1 - fy) +
                        (v10 * (1 - fx) + v11 * fx) * fy
                    ).toFloat()
            }
        }
        return out
    }

    private fun jetColor(x: Double): IntArray {
        fun cl(v: Double) = v.coerceIn(0.0, 1.0)
        return intArrayOf(
            (cl(1.5 - abs(4 * x - 3)) * 255).roundToInt(),
            (cl(1.5 - abs(4 * x - 2)) * 255).roundToInt(),
            (cl(1.5 - abs(4 * x - 1)) * 255).roundToInt(),
        )
    }

    /** Port of the website's focusBox(). */
    private fun focusBox(cam: FloatArray, w: Int, h: Int): FloatArray {
        var thr = 0.55f
        var mask = ArrayList<Int>()
        for (t in 0..1) {
            mask = ArrayList()
            for (i in 0 until w * h) {
                if (cam[i] >= thr) mask.add(i)
            }
            if (mask.size >= 0.01 * w * h || t == 1) break
            thr = 0.40f
        }
        var x0 = w
        var x1 = 0
        var y0 = h
        var y1 = 0
        for (i in mask) {
            val x = i % w
            val y = i / w
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        var bw = x1 - x0 + 1
        var bh = y1 - y0 + 1
        if (bw > 0.85 * w || bh > 0.85 * h) {
            var peak = 0
            var pv = -1.0f
            for (i in 0 until w * h) {
                if (cam[i] > pv) {
                    pv = cam[i]
                    peak = i
                }
            }
            val px = peak % w
            val py = peak / w
            bw = (0.46 * w).toInt()
            bh = (0.46 * h).toInt()
            x0 = px - (bw shr 1)
            y0 = py - (bh shr 1)
            x1 = x0 + bw
            y1 = y0 + bh
        }
        val pad = 8
        x0 = max(0, x0 - pad)
        y0 = max(0, y0 - pad)
        x1 = min(w - 1, x1 + pad)
        y1 = min(h - 1, y1 + pad)
        return floatArrayOf(
            x0 / w.toFloat(),
            y0 / h.toFloat(),
            (x1 - x0 + 1) / w.toFloat(),
            (y1 - y0 + 1) / h.toFloat(),
        )
    }
}
