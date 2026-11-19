package com.shadhinkhan.breastai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shadhinkhan.breastai.R
import com.shadhinkhan.breastai.core.DISCLAIMER
import com.shadhinkhan.breastai.core.STAT_ACCURACY
import com.shadhinkhan.breastai.core.STAT_MACRO_F1
import com.shadhinkhan.breastai.core.STAT_ROC_AUC
import com.shadhinkhan.breastai.core.TEAM
import com.shadhinkhan.breastai.core.TeamMember
import com.shadhinkhan.breastai.ui.SectionTitle
import com.shadhinkhan.breastai.ui.entrance
import com.shadhinkhan.breastai.ui.glass
import com.shadhinkhan.breastai.ui.pressScale

private val FB_BLUE = Color(0xFF1877F2)
private val IG_PINK = Color(0xFFE4405F)
private val GH_PURPLE = Color(0xFF6E5494)

fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // No browser available — nothing sensible to do.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onMember: (Int) -> Unit) {
    var showCamInfo by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // ---- App header card ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(0)
                .glass(MaterialTheme.shapes.extraLarge)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF0EA5C6), Color(0xFF8B5CF6))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MonitorHeart, null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Breast Ultrasound AI",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Explainable classification · Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(22.dp))

        SectionTitle("The minds behind")
        Spacer(Modifier.height(10.dp))
        TEAM.forEachIndexed { index, member ->
            Box(Modifier.entrance(1 + index)) {
                MemberCard(member) { onMember(index) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("About the model")
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            InfoRow("Architecture", "EfficientNet-B3 ensemble (3 seeds)")
            InfoRow("Dataset", "BUSI — 780 breast ultrasound images")
            InfoRow("Test accuracy", STAT_ACCURACY)
            InfoRow("Macro F1", STAT_MACRO_F1)
            InfoRow("ROC-AUC", STAT_ROC_AUC)
            InfoRow("Explainability", "Analytic Grad-CAM heatmap")
            InfoRow("Privacy", "100% on-device — nothing is uploaded", last = true)
        }
        Spacer(Modifier.height(10.dp))

        val camInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(MaterialTheme.shapes.large)
                .pressScale(camInteraction)
                .clickable(interactionSource = camInteraction, indication = null) {
                    showCamInfo = true
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("What is Grad-CAM?", fontWeight = FontWeight.Bold)
                Text(
                    "How the heatmap explains each decision",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

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
        Spacer(Modifier.height(24.dp))
    }

    if (showCamInfo) {
        ModalBottomSheet(
            onDismissRequest = { showCamInfo = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    "What is Grad-CAM?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Grad-CAM (Gradient-weighted Class Activation Mapping) shows " +
                        "which parts of the scan the AI paid most attention to. " +
                        "Red and yellow areas influenced the decision most; blue " +
                        "areas mattered least.\n\nThis app computes it analytically " +
                        "from the models' own feature weights — the same method " +
                        "used in the thesis — so the heatmap always matches the " +
                        "prediction it explains. It is a sensitivity map, not " +
                        "medical proof.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

/** Brand identities for the social buttons: real brand gradients with
 *  white glyphs and a soft brand-coloured glow — the Flutter look. */
enum class SocialKind { Facebook, Instagram, Tiktok, Github }

fun socialBrush(kind: SocialKind): Brush = when (kind) {
    SocialKind.Facebook -> Brush.linearGradient(
        listOf(Color(0xFF42A5F5), Color(0xFF1877F2), Color(0xFF0D5BD7)),
        start = Offset.Zero, end = Offset.Infinite,
    )
    SocialKind.Instagram -> Brush.linearGradient(
        listOf(
            Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976),
            Color(0xFF962FBF), Color(0xFF4F5BD5),
        ),
        start = Offset.Zero, end = Offset.Infinite,
    )
    SocialKind.Tiktok -> Brush.linearGradient(
        listOf(Color(0xFF2A2A35), Color(0xFF0B0B10)),
        start = Offset.Zero, end = Offset.Infinite,
    )
    SocialKind.Github -> Brush.linearGradient(
        listOf(Color(0xFF6E5494), Color(0xFF24292E)),
        start = Offset.Zero, end = Offset.Infinite,
    )
}

fun socialGlow(kind: SocialKind): Color = when (kind) {
    SocialKind.Facebook -> Color(0xFF1877F2)
    SocialKind.Instagram -> Color(0xFFD62976)
    SocialKind.Tiktok -> Color(0xFFFE2C55)
    SocialKind.Github -> Color(0xFF6E5494)
}

@Composable
fun BrandButton(kind: SocialKind, resId: Int, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(
                9.dp, CircleShape, clip = false,
                ambientColor = socialGlow(kind), spotColor = socialGlow(kind),
            )
            .clip(CircleShape)
            .background(socialBrush(kind))
            .then(
                if (kind == SocialKind.Tiktok) {
                    Modifier.border(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFF25F4EE), Color(0xFFFE2C55))
                        ),
                        CircleShape,
                    )
                } else {
                    Modifier
                }
            )
            .pressScale(interaction, 0.88f)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(resId),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun MemberCard(member: TeamMember, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.large)
            .border(1.dp, member.color.copy(alpha = 0.5f), MaterialTheme.shapes.large)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(member.color.copy(alpha = 0.15f))
                    .border(1.5.dp, member.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = member.color,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(member.color.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        member.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = member.color,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandButton(SocialKind.Facebook, R.drawable.ic_facebook, "Facebook") {
                openUrl(context, member.facebook)
            }
            BrandButton(SocialKind.Instagram, R.drawable.ic_instagram, "Instagram") {
                openUrl(context, member.instagram)
            }
            BrandButton(SocialKind.Tiktok, R.drawable.ic_tiktok, "TikTok") {
                openUrl(context, member.tiktok)
            }
            if (member.github != null) {
                BrandButton(SocialKind.Github, R.drawable.ic_github, "GitHub") {
                    openUrl(context, member.github)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(118.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
    if (!last) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
