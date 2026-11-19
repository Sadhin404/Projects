package com.shadhinkhan.breastai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The app's "glass": a calm translucent surface with a hairline gradient
 * border — custom Compose graphics, no backdrop blur (smoothness first).
 */
fun Modifier.glass(shape: Shape): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val borderBrush = Brush.linearGradient(
        listOf(
            scheme.onSurface.copy(alpha = 0.16f),
            scheme.onSurface.copy(alpha = 0.05f),
        )
    )
    this
        .clip(shape)
        .background(scheme.surface.copy(alpha = 0.88f))
        .border(1.dp, borderBrush, shape)
}

/** Subtle press-down scale for tappable cards and primary actions. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .glass(MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** Neon accents from the Flutter design. */
val NEON_CYAN = Color(0xFF22D3EE)
val NEON_VIOLET = Color(0xFFA78BFA)
val NEON_PINK = Color(0xFFFB7185)
val NEON_ACCENTS = listOf(NEON_CYAN, NEON_VIOLET, NEON_PINK)

/** Stat card with a per-card neon glow rim — the Flutter NeonStatCard look. */
@Composable
fun NeonStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .shadow(10.dp, shape, clip = false, ambientColor = accent, spotColor = accent)
            .glass(shape)
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            maxLines = 1,
        )
    }
}

/** Staggered entrance: fades in while rising a little, delayed per index. */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 75L)
        anim.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 26.dp.toPx()
    }
}

/** Static ambient colour glows behind the content (the Flutter v3 look —
 *  pretty but zero GPU-heavy animation). */
@Composable
fun AmbientGlow() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(330.dp)
                .align(Alignment.TopStart)
                .offset(x = (-95).dp, y = (-75).dp)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = 0.17f), Color.Transparent)
                    )
                ),
        )
        Box(
            Modifier
                .size(310.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 95.dp, y = 85.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF8B5CF6).copy(alpha = 0.14f), Color.Transparent)
                    )
                ),
        )
    }
}

/** Primary call-to-action with the teal → violet gradient + press effect. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .shadow(
                14.dp,
                RoundedCornerShape(16.dp),
                clip = false,
                ambientColor = Color(0xFF0EA5C6),
                spotColor = Color(0xFF8B5CF6),
            )
            .pressScale(interaction)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0EA5C6), Color(0xFF8B5CF6))
                )
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Donut ring with centred content — used for the prediction confidence. */
@Composable
fun ConfidenceRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    stroke: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            drawCircle(
                color = color.copy(alpha = 0.14f),
                style = Stroke(width = sw),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
