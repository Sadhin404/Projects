package com.shadhinkhan.breastai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shadhinkhan.breastai.ui.AmbientGlow
import com.shadhinkhan.breastai.ui.screens.AboutScreen
import com.shadhinkhan.breastai.ui.screens.HistoryScreen
import com.shadhinkhan.breastai.ui.screens.HomeScreen
import com.shadhinkhan.breastai.ui.screens.MemberScreen
import com.shadhinkhan.breastai.ui.screens.OnboardingScreen
import com.shadhinkhan.breastai.ui.screens.ResultScreen
import com.shadhinkhan.breastai.ui.screens.SplashScreen

/** Caps content width on wide screens (tablets / foldables) — responsive
 *  behaviour carried over from the Flutter app. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AmbientGlow()
        Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            content()
        }
    }
}

/**
 * Floating glass bottom bar: translucent surface matched to the app
 * background, a gradient pill with a soft glow behind the selected tab,
 * and colour-animated icons and labels.
 */
@Composable
private fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .shadow(
                    18.dp,
                    RoundedCornerShape(30.dp),
                    clip = false,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary,
                )
                .glass(RoundedCornerShape(30.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab(0, Icons.Filled.Home, "Home", tab, onTab)
            NavTab(1, Icons.Filled.History, "History", tab, onTab)
            NavTab(2, Icons.Filled.Info, "About", tab, onTab)
        }
    }
}

@Composable
private fun RowScope.NavTab(
    index: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tab: Int,
    onTab: (Int) -> Unit,
) {
    val selected = tab == index
    val glow by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(280),
        label = "navGlow",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) Color.White
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(280),
        label = "navIconTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(280),
        label = "navLabelColor",
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null) { onTab(index) }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // soft halo behind the selected pill
            Box(
                modifier = Modifier
                    .size(width = 66.dp, height = 44.dp)
                    .graphicsLayer { alpha = glow * 0.85f }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF8B5CF6).copy(alpha = 0.5f),
                                Color.Transparent,
                            ),
                        ),
                        RoundedCornerShape(22.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 33.dp)
                    .clip(RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = glow }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0EA5C6), Color(0xFF8B5CF6)),
                            ),
                        ),
                )
                Icon(
                    icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun AppNav(vm: MainViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "splash") {
        composable("splash") {
            SplashScreen {
                nav.navigate(if (vm.seenOnboarding) "main" else "onboarding") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
        composable("onboarding") {
            OnboardingScreen {
                vm.completeOnboarding()
                nav.navigate("main") {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        }
        composable("main") {
            MainScaffold(
                vm = vm,
                onOpenResult = { nav.navigate("result") },
                onMember = { nav.navigate("member/$it") },
            )
        }
        composable("result") {
            Centered {
                ResultScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
        composable(
            "member/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType }),
        ) { entry ->
            Centered {
                MemberScreen(
                    index = entry.arguments?.getInt("index") ?: 0,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    vm: MainViewModel,
    onOpenResult: () -> Unit,
    onMember: (Int) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            FloatingNavBar(tab = tab, onTab = { tab = it })
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            AmbientGlow()
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(vm, onOpenResult)
                    1 -> HistoryScreen(vm, onOpenResult)
                    else -> AboutScreen(onMember)
                }
            }
        }
    }
}
