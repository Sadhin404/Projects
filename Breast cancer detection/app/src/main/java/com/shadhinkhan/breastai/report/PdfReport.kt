package com.shadhinkhan.breastai.report

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shadhinkhan.breastai.core.CLASS_DISPLAY
import com.shadhinkhan.breastai.core.CLASS_MEANING
import com.shadhinkhan.breastai.core.CLASS_NEXT_STEP
import com.shadhinkhan.breastai.core.CREDIT_LINE
import com.shadhinkhan.breastai.core.CREDIT_ORG
import com.shadhinkhan.breastai.core.DISCLAIMER
import com.shadhinkhan.breastai.core.STAT_ACCURACY
import com.shadhinkhan.breastai.core.STAT_MACRO_F1
import com.shadhinkhan.breastai.core.STAT_ROC_AUC
import com.shadhinkhan.breastai.engine.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the same report the project website generates — header, model
 * stat chips, prediction hero with confidence donut, class probability
 * bars, Grad-CAM visuals (with the focus box + label drawn on the
 * overlay, like the website), next step, disclaimer and credits.
 */
object PdfReport {

    fun reportId(r: AnalysisResult): String =
        "BUAI-" + r.id.takeLast(6).uppercase()

    private val INK = Color.parseColor("#16242F")
    private val GREY = Color.parseColor("#64798A")
    private val HAIR = Color.parseColor("#DCE7EE")
    private val TEAL = Color.parseColor("#0E7490")
    private val VIOLET = Color.parseColor("#8B5CF6")
    private val CARD = Color.parseColor("#F3F8FB")

    private fun accentOf(key: String) = Color.parseColor(
        when (key) {
            "normal" -> "#059669"
            "benign" -> "#D97706"
            else -> "#E11D48"
        }
    )

    private fun tintOf(key: String) = Color.parseColor(
        when (key) {
            "normal" -> "#ECFDF5"
            "benign" -> "#FFFBEB"
            else -> "#FFF1F2"
        }
    )

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint().apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        isAntiAlias = true
    }

    private fun layoutOf(text: String, p: TextPaint, width: Float, lineMult: Float = 1.25f) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineMult)
            .setIncludePad(false)
            .build()

    /** Draws wrapped text; returns the height consumed. */
    private fun wrapped(
        canvas: Canvas, text: String, p: TextPaint,
        x: Float, y: Float, width: Float, lineMult: Float = 1.25f,
    ): Float {
        val layout = layoutOf(text, p, width, lineMult)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Measures wrapped text height without drawing anything. */
    private fun measure(text: String, p: TextPaint, width: Float): Float =
        layoutOf(text, p, width).height.toFloat()

    /**
     * Saves the report straight into the phone's Downloads folder via
     * MediaStore (no permission needed on Android 10+). Returns false on
     * older Android versions or on any I/O failure.
     */
    suspend fun downloadToDownloads(context: Context, r: AnalysisResult): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val file = buildPdfFile(context, r)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun buildPdfFile(context: Context, r: AnalysisResult): File =
        withContext(Dispatchers.IO) {
            val accent = accentOf(r.predictedKey)
            val tint = tintOf(r.predictedKey)
            val pct = String.format(Locale.US, "%.1f", r.confidence * 100)
            val dateStr = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.US)
                .format(Date(r.createdAt))

            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val c = page.canvas
            val left = 42f
            val right = 595f - 42f
            val width = right - left
            var y = 36f

            // ---- Header ----
            val logoPaint = Paint().apply { color = TEAL; isAntiAlias = true }
            c.drawRoundRect(RectF(left, y, left + 30, y + 30), 8f, 8f, logoPaint)
            val aiPaint = paint(13f, Color.WHITE, bold = true)
            aiPaint.textAlign = Paint.Align.CENTER
            c.drawText("AI", left + 15, y + 20.5f, aiPaint)
            c.drawText("Breast Ultrasound AI", left + 40, y + 14, paint(16f, INK, bold = true))
            c.drawText(
                "Explainable Classification Report", left + 40, y + 27,
                paint(9.5f, GREY)
            )
            val idPaint = paint(10.5f, INK, bold = true).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(reportId(r), right, y + 12, idPaint)
            val datePaint = paint(9.5f, GREY).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(dateStr, right, y + 25, datePaint)
            y += 38f
            val rulePaint = Paint().apply { isAntiAlias = true }
            rulePaint.color = TEAL
            c.drawRect(left, y, left + width * 0.55f, y + 3, rulePaint)
            rulePaint.color = VIOLET
            c.drawRect(left + width * 0.55f, y, right, y + 3, rulePaint)
            y += 15f

            // ---- Stat chips ----
            val chips = listOf(
                "MODEL" to "EfficientNet-B3 × 3",
                "TEST ACCURACY" to STAT_ACCURACY,
                "MACRO F1" to STAT_MACRO_F1,
                "ROC-AUC" to STAT_ROC_AUC,
            )
            val chipGap = 6f
            val chipW = (width - chipGap * 3) / 4
            val chipPaint = Paint().apply { color = CARD; isAntiAlias = true }
            chips.forEachIndexed { i, (label, value) ->
                val cx = left + i * (chipW + chipGap)
                c.drawRoundRect(RectF(cx, y, cx + chipW, y + 30), 6f, 6f, chipPaint)
                val lp = paint(6.8f, GREY, bold = true).apply { textAlign = Paint.Align.CENTER }
                c.drawText(label, cx + chipW / 2, y + 12, lp)
                val vp = paint(9.3f, INK, bold = true).apply { textAlign = Paint.Align.CENTER }
                c.drawText(value, cx + chipW / 2, y + 24.5f, vp)
            }
            y += 44f

            // ---- Prediction hero ----
            val heroPaint = Paint().apply { color = tint; isAntiAlias = true }
            val donutCx = right - 16 - 37
            val heroTextW = (donutCx - 37 - 12) - (left + 16)
            val meaningH = measure(CLASS_MEANING[r.predictedKey]!!, paint(10f, INK), heroTextW)
            val heroH = maxOf(98f, 12 + 8 + 4 + 38 + 4 + meaningH + 12)
            c.drawRoundRect(RectF(left, y, right, y + heroH), 10f, 10f, heroPaint)
            c.drawText("AI PREDICTION", left + 16, y + 12 + 9, paint(8.5f, GREY, bold = true))
            c.drawText(
                r.predictedDisplay.uppercase(), left + 16, y + 12 + 8 + 4 + 32,
                paint(30f, accent, bold = true)
            )
            wrapped(
                c, CLASS_MEANING[r.predictedKey]!!, paint(10f, INK),
                left + 16, y + 12 + 8 + 4 + 38 + 6, heroTextW
            )
            val donutCy = y + heroH / 2
            val ringPaint = Paint().apply {
                color = accent; style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true
            }
            c.drawCircle(donutCx, donutCy, 37f, ringPaint)
            val pctPaint = paint(15f, INK, bold = true).apply { textAlign = Paint.Align.CENTER }
            c.drawText("$pct%", donutCx, donutCy + 1, pctPaint)
            val confPaint = paint(7.5f, GREY).apply { textAlign = Paint.Align.CENTER }
            c.drawText("confidence", donutCx, donutCy + 13, confPaint)
            y += heroH + 16f

            // ---- Probabilities ----
            c.drawText("Class probabilities", left, y + 11, paint(11.5f, INK, bold = true))
            y += 16f
            val hairPaint = Paint().apply { color = HAIR }
            c.drawRect(left, y, right, y + 0.7f, hairPaint)
            y += 8f
            val probHex = listOf("#059669", "#D97706", "#E11D48")
            for (i in 0..2) {
                val v = r.probabilities[i]
                val dotPaint = Paint().apply {
                    color = Color.parseColor(probHex[i]); isAntiAlias = true
                }
                c.drawCircle(left + 3, y + 4, 3f, dotPaint)
                c.drawText(CLASS_DISPLAY[i], left + 12, y + 7.5f, paint(10f, INK))
                val trackX = left + 12 + 62
                val trackW = width - 12 - 62 - 8 - 44
                val trackPaint = Paint().apply {
                    color = Color.parseColor("#E3EDF3"); isAntiAlias = true
                }
                c.drawRoundRect(RectF(trackX, y, trackX + trackW, y + 7), 3.5f, 3.5f, trackPaint)
                val fillPaint = Paint().apply {
                    color = Color.parseColor(probHex[i]); isAntiAlias = true
                }
                c.drawRoundRect(
                    RectF(trackX, y, trackX + (trackW * v).toFloat(), y + 7),
                    3.5f, 3.5f, fillPaint
                )
                val vp = paint(10f, INK, bold = true).apply { textAlign = Paint.Align.RIGHT }
                c.drawText(
                    String.format(Locale.US, "%.1f%%", v * 100), right, y + 7.5f, vp
                )
                y += 15f
            }
            y += 4f

            // ---- Grad-CAM images ----
            c.drawText(
                "Visual explanation — Grad-CAM", left, y + 11,
                paint(11.5f, INK, bold = true)
            )
            y += 16f
            c.drawRect(left, y, right, y + 0.7f, hairPaint)
            y += 6f
            val cellGap = 6f
            val cellW = (width - cellGap * 2) / 3
            val cellH = 130f
            val images = listOf(
                r.original to "Original scan",
                r.heatmap to "Grad-CAM heatmap",
                r.overlay to "Overlay",
            )
            images.forEachIndexed { idx, (bmp, label) ->
                val cx = left + idx * (cellW + cellGap)
                // Cover-crop: source is square; display cellW x cellH.
                val srcH = bmp.width * (cellH / cellW)
                val srcTop = ((bmp.height - srcH) / 2).toInt()
                val src = Rect(0, srcTop, bmp.width, srcTop + srcH.toInt())
                val dst = RectF(cx, y, cx + cellW, y + cellH)
                c.drawBitmap(bmp, src, dst, Paint().apply { isFilterBitmap = true })
                if (idx == 2) {
                    // Focus box + predicted label on the overlay, like the website.
                    val cropTop = (cellW - cellH) / 2
                    val bx = cx + r.bbox[0] * cellW
                    val by = y + r.bbox[1] * cellW - cropTop
                    val bw = r.bbox[2] * cellW
                    val bh = r.bbox[3] * cellW
                    val boxPaint = Paint().apply {
                        color = accent; style = Paint.Style.STROKE
                        strokeWidth = 1.8f; isAntiAlias = true
                    }
                    c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 4f, 4f, boxPaint)
                    val labelText = r.predictedDisplay.uppercase()
                    val lp = paint(6.5f, Color.WHITE, bold = true)
                    val lw = lp.measureText(labelText) + 8
                    val ly = if (by - cropTop > 12f) by - 11 else by + 2
                    val labelPaint = Paint().apply { color = accent; isAntiAlias = true }
                    c.drawRect(bx, ly, bx + lw, ly + 11, labelPaint)
                    c.drawText(labelText, bx + 4, ly + 8.2f, lp)
                }
                val capPaint = paint(8.5f, GREY).apply { textAlign = Paint.Align.CENTER }
                c.drawText(label, cx + cellW / 2, y + cellH + 11, capPaint)
            }
            y += cellH + 18f
            c.drawText(
                "Red and yellow influenced the decision most. The heatmap is a sensitivity map, not medical proof.",
                left, y + 8, paint(9f, GREY)
            )
            y += 22f

            // ---- Next step ----
            val stepTextH = measure(CLASS_NEXT_STEP[r.predictedKey]!!, paint(9.8f, INK), width - 20)
            val stepBoxH = 10 + 11 + 3 + stepTextH + 10
            val cardPaint = Paint().apply { color = CARD; isAntiAlias = true }
            c.drawRoundRect(RectF(left, y, right, y + stepBoxH), 8f, 8f, cardPaint)
            c.drawText(
                "RECOMMENDED NEXT STEP", left + 10, y + 10 + 8,
                paint(8f, TEAL, bold = true)
            )
            wrapped(
                c, CLASS_NEXT_STEP[r.predictedKey]!!, paint(9.8f, INK),
                left + 10, y + 10 + 11 + 5, width - 20
            )
            y += stepBoxH + 10f

            // ---- Disclaimer ----
            val discTextH = measure(DISCLAIMER, paint(9f, INK), width - 20)
            val discBoxH = 10 + 11 + 3 + discTextH + 10
            val discPaint = Paint().apply {
                color = Color.parseColor("#FDF3F3"); isAntiAlias = true
            }
            c.drawRoundRect(RectF(left, y, right, y + discBoxH), 8f, 8f, discPaint)
            c.drawText(
                "IMPORTANT — NOT A MEDICAL DIAGNOSIS", left + 10, y + 10 + 8.5f,
                paint(8.5f, Color.parseColor("#9F1239"), bold = true)
            )
            wrapped(c, DISCLAIMER, paint(9f, INK), left + 10, y + 10 + 11 + 5, width - 20)

            // ---- Footer ----
            val footY = 842f - 36f - 34f
            c.drawRect(left, footY, right, footY + 0.7f, hairPaint)
            c.drawText(CREDIT_LINE, left, footY + 13, paint(8.6f, INK, bold = true))
            c.drawText(CREDIT_ORG, left, footY + 24, paint(8.4f, GREY))
            c.drawText(
                "Dataset: BUSI — Al-Dhabyani et al., Data in Brief (2020)",
                left, footY + 34, paint(8f, GREY)
            )

            doc.finishPage(page)
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, "Ultrasound_Report_${reportId(r)}.pdf")
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            doc.close()
            file
        }
}
