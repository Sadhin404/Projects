package com.shadhinkhan.breastai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.shadhinkhan.breastai.core.CLASS_COLORS
import com.shadhinkhan.breastai.core.CLASS_DISPLAY
import com.shadhinkhan.breastai.core.CLASS_KEYS
import com.shadhinkhan.breastai.core.CLASS_MEANING
import com.shadhinkhan.breastai.core.CLASS_NEXT_STEP
import com.shadhinkhan.breastai.core.DISCLAIMER
import com.shadhinkhan.breastai.report.PdfReport
import com.shadhinkhan.breastai.ui.ConfidenceRing
import com.shadhinkhan.breastai.ui.GradientButton
import com.shadhinkhan.breastai.ui.MainViewModel
import com.shadhinkhan.breastai.ui.SectionTitle
import com.shadhinkhan.breastai.ui.entrance
import com.shadhinkhan.breastai.ui.glass
import com.shadhinkhan.breastai.ui.pressScale
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: MainViewModel, onBack: () -> Unit) {
    val r = vm.currentResult
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    "Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (r != null) {
                    Text(
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
                            .format(Date(r.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (r == null) {
            Spacer(Modifier.height(40.dp))
            Text(
                "No result to show yet — analyse a scan first.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val accent = CLASS_COLORS[r.predictedKey]!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(6.dp))

            // ---- Prediction hero (accent glow, like the Flutter design) ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(0)
                    .shadow(
                        14.dp, MaterialTheme.shapes.extraLarge,
                        clip = false, ambientColor = accent, spotColor = accent,
                    )
                    .glass(MaterialTheme.shapes.extraLarge)
                    .border(1.5.dp, accent.copy(alpha = 0.45f), MaterialTheme.shapes.extraLarge)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "AI PREDICTION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                    )
                    Text(
                        r.predictedDisplay,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CLASS_MEANING[r.predictedKey]!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ConfidenceRing(
                    progress = r.confidence.toFloat(),
                    color = accent,
                    size = 88.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            String.format(Locale.US, "%.1f%%", r.confidence * 100),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "confidence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---- Image viewer ----
            SectionTitle("Visual explanation")
            Spacer(Modifier.height(10.dp))
            var mode by remember { mutableIntStateOf(2) } // 0 original, 1 heatmap, 2 overlay
            var blend by remember { mutableFloatStateOf(0.45f) }
            val modes = listOf("Original", "Heatmap", "Overlay")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i,
                        onClick = { mode = i },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .entrance(1)
                    .clip(MaterialTheme.shapes.extraLarge),
            ) {
                when (mode) {
                    0 -> Image(
                        bitmap = r.original.asImageBitmap(),
                        contentDescription = "Original scan",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> Image(
                        bitmap = r.heatmap.asImageBitmap(),
                        contentDescription = "Grad-CAM heatmap",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        Image(
                            bitmap = r.original.asImageBitmap(),
                            contentDescription = "Overlay base",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Image(
                            bitmap = r.heatmap.asImageBitmap(),
                            contentDescription = "Overlay heatmap",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(blend),
                        )
                        // Focus box + predicted label.
                        val bx = maxWidth * r.bbox[0]
                        val by = maxHeight * r.bbox[1]
                        val bw = maxWidth * r.bbox[2]
                        val bh = maxHeight * r.bbox[3]
                        Box(
                            modifier = Modifier
                                .offset(x = bx, y = by)
                                .size(bw, bh)
                                .border(2.dp, accent, RoundedCornerShape(6.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = bx, y = (by - 22.dp).coerceAtLeast(0.dp))
                                .clip(RoundedCornerShape(5.dp))
                                .background(accent)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                r.predictedDisplay.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            if (mode == 2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        "Original",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = blend,
                        onValueChange = { blend = it },
                        valueRange = 0.15f..0.85f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Heatmap",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Red and yellow influenced the decision most.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            // ---- Probabilities: Vico chart + exact bars ----
            SectionTitle("Class probabilities")
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(2)
                    .glass(MaterialTheme.shapes.large)
                    .padding(16.dp),
            ) {
                val producer = remember { CartesianChartModelProducer() }
                LaunchedEffect(r.id) {
                    producer.runTransaction {
                        columnSeries { series(r.probabilities.toList()) }
                    }
                }
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = { _, y, _ -> "${(y * 100).roundToInt()}%" },
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = { _, x, _ ->
                                CLASS_DISPLAY[x.roundToInt().coerceIn(0, 2)]
                            },
                        ),
                    ),
                    modelProducer = producer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                )
                Spacer(Modifier.height(12.dp))
                for (i in 0..2) {
                    val c = CLASS_COLORS[CLASS_KEYS[i]]!!
                    val v = r.probabilities[i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 5.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            CLASS_DISPLAY[i],
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(76.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(v.toFloat().coerceIn(0f, 1f))
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(c),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            String.format(Locale.US, "%.1f%%", v * 100),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(52.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---- Next step ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(3)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
            ) {
                Text(
                    "RECOMMENDED NEXT STEP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    CLASS_NEXT_STEP[r.predictedKey]!!,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp),
            ) {
                Text(
                    DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(20.dp))

            GradientButton(
                text = "Download PDF report",
                icon = Icons.Filled.Download,
                onClick = {
                    scope.launch {
                        val ok = PdfReport.downloadToDownloads(context, r)
                        Toast.makeText(
                            context,
                            if (ok) "PDF saved to Downloads"
                            else "Could not save the PDF",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.clearPicked()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Analyse another scan")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
