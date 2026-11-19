package com.shadhinkhan.breastai.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.shadhinkhan.breastai.R
import com.shadhinkhan.breastai.core.DISCLAIMER
import com.shadhinkhan.breastai.core.NOT_SCAN_MESSAGE
import com.shadhinkhan.breastai.core.STATS
import com.shadhinkhan.breastai.ui.AnalysisState
import com.shadhinkhan.breastai.ui.GradientButton
import com.shadhinkhan.breastai.ui.MainViewModel
import com.shadhinkhan.breastai.ui.NEON_ACCENTS
import com.shadhinkhan.breastai.ui.NeonStatCard
import com.shadhinkhan.breastai.ui.STAGE_LABELS
import com.shadhinkhan.breastai.ui.SectionTitle
import com.shadhinkhan.breastai.ui.entrance
import com.shadhinkhan.breastai.ui.glass
import com.shadhinkhan.breastai.ui.pressScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpenResult: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showSteps by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() } ?: return@launch
                    var name = "Selected image"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                        val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cur.moveToFirst()) {
                            cur.getString(idx)?.let { name = it }
                        }
                    }
                    vm.onImagePicked(bytes, name)
                } catch (_: Exception) {
                    // Unreadable file — leave the current selection as-is.
                }
            }
        }
    }

    fun pickSample(resId: Int, label: String) {
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        vm.onImagePicked(bytes, label)
        scope.launch { scrollState.animateScrollTo(0) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ---- Header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0EA5C6), Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MonitorHeart, null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Breast Ultrasound AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Explainable scan analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.toggleTheme() }) {
                    Icon(
                        if (vm.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Toggle theme",
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            // Animated gradient headline (the Flutter text animation).
            val headlineTransition = rememberInfiniteTransition(label = "headline")
            val gradShift by headlineTransition.animateFloat(
                initialValue = 0f,
                targetValue = 300f,
                animationSpec = infiniteRepeatable(tween(3400), RepeatMode.Reverse),
                label = "gradShift",
            )
            Text(
                "Analyse a breast ultrasound scan",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF22D3EE),
                            Color(0xFFA78BFA),
                            Color(0xFF22D3EE),
                        ),
                        start = Offset(gradShift, 0f),
                        end = Offset(gradShift + 480f, 70f),
                    ),
                ),
                modifier = Modifier.entrance(0),
            )
            Spacer(Modifier.height(4.dp))
            TypewriterLines(
                lines = listOf(
                    "On-device AI with a visual explanation of every decision.",
                    "Three EfficientNet-B3 models examine every scan together.",
                    "Grad-CAM highlights the region behind each decision.",
                    "100% private — your scans never leave this phone.",
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .entrance(0),
            )
            Spacer(Modifier.height(16.dp))

            // ---- Model stats (neon glow cards) ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.entrance(1),
            ) {
                STATS.forEachIndexed { i, s ->
                    NeonStatCard(s.label, s.value, NEON_ACCENTS[i], Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---- Upload card ----
            Box(Modifier.entrance(2)) {
                UploadCard(
                    vm = vm,
                    onPick = { picker.launch("image/*") },
                    onAnalyse = { vm.analyze(onOpenResult) },
                )
            }

            // ---- Banners ----
            when (val st = vm.analysisState) {
                is AnalysisState.NotScan -> {
                    Spacer(Modifier.height(12.dp))
                    Banner(NOT_SCAN_MESSAGE)
                }
                is AnalysisState.Failed -> {
                    Spacer(Modifier.height(12.dp))
                    Banner(st.message)
                }
                else -> {}
            }
            Spacer(Modifier.height(22.dp))

            // ---- Samples ----
            SectionTitle("Try a sample scan", modifier = Modifier.entrance(3))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .entrance(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SampleTile(R.drawable.sample_normal, "Normal", Modifier.fillMaxWidth()) {
                        pickSample(R.drawable.sample_normal, "Sample — Normal")
                    }
                    SampleTile(R.drawable.sample_malignant, "Malignant", Modifier.fillMaxWidth()) {
                        pickSample(R.drawable.sample_malignant, "Sample — Malignant")
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    SampleTile(R.drawable.sample_benign, "Benign", Modifier.fillMaxWidth()) {
                        pickSample(R.drawable.sample_benign, "Sample — Benign")
                    }
                }
            }
            Spacer(Modifier.height(22.dp))

            // ---- How it works ----
            val stepsInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(MaterialTheme.shapes.large)
                    .pressScale(stepsInteraction)
                    .clickable(interactionSource = stepsInteraction, indication = null) {
                        showSteps = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "How it works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Four steps, all on this phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.PlayArrow, null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }

        // ---- Analysing overlay ----
        val st = vm.analysisState
        if (st is AnalysisState.Analyzing) {
            AnalyzingOverlay(stageLabel = STAGE_LABELS[st.stage], preview = vm.pickedPreview)
        }
    }

    if (showSteps) {
        ModalBottomSheet(
            onDismissRequest = { showSteps = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    "How it works",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                val steps = listOf(
                    "Upload a scan" to "Choose a breast ultrasound image from your phone.",
                    "Image check" to "The app first confirms it really looks like an ultrasound scan.",
                    "AI analysis" to "Three EfficientNet-B3 models examine it and vote together.",
                    "Explanation" to "A Grad-CAM heatmap highlights the region behind the decision.",
                )
                steps.forEachIndexed { i, (title, body) ->
                    Row(Modifier.padding(vertical = 7.dp)) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.Bold)
                            Text(
                                body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun UploadCard(vm: MainViewModel, onPick: () -> Unit, onAnalyse: () -> Unit) {
    val preview = vm.pickedPreview
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.extraLarge)
            .padding(16.dp),
    ) {
        if (preview == null) {
            val pickInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedBorder(
                        color = MaterialTheme.colorScheme.outline,
                        cornerRadius = 18.dp,
                    )
                    .pressScale(pickInteraction)
                    .clickable(interactionSource = pickInteraction, indication = null) {
                        onPick()
                    }
                    .padding(vertical = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UploadVisual()
                    Spacer(Modifier.height(6.dp))
                    Text("Upload a scan image", fontWeight = FontWeight.Bold)
                    Text(
                        "PNG or JPG · runs 100% on-device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = "Choose image",
                icon = Icons.Filled.Image,
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Selected scan",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.pickedName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    val kb = (vm.pickedBytes?.size ?: 0) / 1024
                    Text(
                        "$kb KB · ready to analyse",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.clearPicked() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove image")
                }
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = "Analyse scan",
                onClick = onAnalyse,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text("Choose a different image")
            }
        }
    }
}

@Composable
private fun Banner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.WarningAmber, null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Rotating typewriter lines — the Flutter animated-text effect: each line
 * types in, rests, deletes, and the next line follows, with a blinking
 * cursor. (Picked from the classic animated-text set.)
 */
@Composable
private fun TypewriterLines(lines: List<String>, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(lines) {
        var idx = 0
        while (true) {
            val target = lines[idx % lines.size]
            for (i in 1..target.length) {
                text = target.substring(0, i)
                delay(26)
            }
            delay(1700)
            for (i in target.length downTo 0) {
                text = target.substring(0, i)
                delay(7)
            }
            delay(260)
            idx++
        }
    }
    val cursorTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    val primary = MaterialTheme.colorScheme.primary
    val annotated = remember(text, cursorAlpha) {
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = primary.copy(alpha = cursorAlpha))) {
                append("▍")
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Animated upload emblem from the Flutter design: expanding pulse rings,
 *  a floating gradient badge, and two drifting chips. */
@Composable
private fun UploadVisual() {
    val transition = rememberInfiniteTransition(label = "uploadVisual")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Restart),
        label = "pulse",
    )
    val floatY by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "floatY",
    )
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
        listOf(0f, 0.5f).forEach { phase ->
            val p = (pulse + phase) % 1f
            Box(
                Modifier
                    .size(118.dp)
                    .graphicsLayer {
                        val s = 0.5f + 0.55f * p
                        scaleX = s
                        scaleY = s
                        alpha = (1f - p) * 0.75f
                    }
                    .border(2.dp, primary, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(62.dp)
                .offset(y = floatY.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0EA5C6), Color(0xFF8B5CF6))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.UploadFile, null,
                tint = Color.White,
                modifier = Modifier.size(29.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-floatY).dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                "AI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = floatY.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                "Grad-CAM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SampleTile(resId: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1.35f)
            .clip(MaterialTheme.shapes.large)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = "Sample $label scan",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Dashed rounded border drawn with a path effect (Compose has no built-in). */
private fun Modifier.dashedBorder(color: Color, cornerRadius: androidx.compose.ui.unit.Dp): Modifier =
    drawWithCache {
        val stroke = Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
        )
        val radius = androidx.compose.ui.geometry.CornerRadius(
            cornerRadius.toPx(), cornerRadius.toPx()
        )
        onDrawWithContent {
            drawContent()
            drawRoundRect(color = color, style = stroke, cornerRadius = radius)
        }
    }

/** Full-screen calm analysing state: the scan with a soft sweeping beam,
 *  like the project website's scanning frame. */
@Composable
private fun AnalyzingOverlay(stageLabel: String, preview: android.graphics.Bitmap?) {
    val transition = rememberInfiniteTransition(label = "beam")
    val beam by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150), RepeatMode.Reverse),
        label = "beamY",
    )
    val borderPulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "borderPulse",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (preview != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = borderPulse),
                            MaterialTheme.shapes.extraLarge,
                        ),
                ) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Sweeping beam (website-style scanning frame).
                    val beamHeight = maxHeight * 0.16f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(beamHeight)
                            .offset(y = (maxHeight - beamHeight) * beam)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        Color.Transparent,
                                    )
                                )
                            ),
                    )
                    CornerBrackets(MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Analysing…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stageLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CornerBrackets(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val len = 26.dp.toPx()
        val sw = 3.dp.toPx()
        val inset = 10.dp.toPx()
        val w = size.width
        val h = size.height
        // top-left
        drawLine(color, Offset(inset, inset), Offset(inset + len, inset), sw)
        drawLine(color, Offset(inset, inset), Offset(inset, inset + len), sw)
        // top-right
        drawLine(color, Offset(w - inset - len, inset), Offset(w - inset, inset), sw)
        drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + len), sw)
        // bottom-left
        drawLine(color, Offset(inset, h - inset), Offset(inset + len, h - inset), sw)
        drawLine(color, Offset(inset, h - inset - len), Offset(inset, h - inset), sw)
        // bottom-right
        drawLine(color, Offset(w - inset - len, h - inset), Offset(w - inset, h - inset), sw)
        drawLine(color, Offset(w - inset, h - inset - len), Offset(w - inset, h - inset), sw)
    }
}
