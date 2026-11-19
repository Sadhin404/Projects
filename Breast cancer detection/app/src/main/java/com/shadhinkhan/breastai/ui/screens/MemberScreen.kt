package com.shadhinkhan.breastai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shadhinkhan.breastai.R
import com.shadhinkhan.breastai.core.TEAM
import com.shadhinkhan.breastai.ui.glass
import com.shadhinkhan.breastai.ui.pressScale

@Composable
fun MemberScreen(index: Int, onBack: () -> Unit) {
    val member = TEAM.getOrNull(index) ?: return
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Team member",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(MaterialTheme.shapes.extraLarge)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(member.color.copy(alpha = 0.15f))
                    .border(2.dp, member.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.initials,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = member.color,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                member.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(member.color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    member.role,
                    style = MaterialTheme.typography.labelLarge,
                    color = member.color,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.School, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Computer Science & Engineering",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "First Capital University of Bangladesh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))

        Text(
            "Connect",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(MaterialTheme.shapes.large)
                .padding(vertical = 6.dp),
        ) {
            SocialRow(SocialKind.Facebook, R.drawable.ic_facebook, "Facebook", "Facebook profile") {
                openUrl(context, member.facebook)
            }
            SocialRow(
                SocialKind.Instagram, R.drawable.ic_instagram, "Instagram",
                handleOf(member.instagram)
            ) { openUrl(context, member.instagram) }
            SocialRow(
                SocialKind.Tiktok, R.drawable.ic_tiktok, "TikTok",
                handleOf(member.tiktok)
            ) { openUrl(context, member.tiktok) }
            if (member.github != null) {
                SocialRow(SocialKind.Github, R.drawable.ic_github, "GitHub", "@Sadhin404") {
                    openUrl(context, member.github)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun handleOf(url: String): String {
    val tail = url.trimEnd('/').substringAfterLast('/')
    return if (tail.startsWith("@")) tail else "@$tail"
}

@Composable
private fun SocialRow(
    kind: SocialKind,
    resId: Int,
    platform: String,
    handle: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    8.dp, CircleShape, clip = false,
                    ambientColor = socialGlow(kind), spotColor = socialGlow(kind),
                )
                .clip(CircleShape)
                .background(socialBrush(kind)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(resId),
                contentDescription = platform,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(platform, fontWeight = FontWeight.SemiBold)
            Text(
                handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.OpenInNew, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
