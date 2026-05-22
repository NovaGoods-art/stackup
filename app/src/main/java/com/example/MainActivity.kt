package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HabitChain
import com.example.ui.ChainWithDetails
import com.example.ui.HeatmapDay
import com.example.ui.StackUpUiState
import com.example.ui.StackUpViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SoftIce
import androidx.compose.foundation.BorderStroke
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                StackUpApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackUpApp(
    viewModel: StackUpViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingChain by remember { mutableStateOf<ChainWithDetails?>(null) }

    // First time template insertion
    LaunchedEffect(uiState.chains) {
        if (uiState.chains.isEmpty()) {
            // Populate pre-designed habit stack templates
            viewModel.createChain(
                name = "Morning Launch Stack",
                anchor = "Brewing morning coffee",
                steps = listOf(
                    "Drink 1 glass of mineral water",
                    "Stretch & do 5 deep breaths",
                    "Write down #1 critical task"
                )
            )
            viewModel.createChain(
                name = "Active Desk Reset",
                anchor = "Sitting down at direct workspace",
                steps = listOf(
                    "Close unnecessary browser tabs",
                    "Locate physical water bottle",
                    "Review list of tasks"
                )
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .testTag("add_stack_fab")
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create New Stack",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Header Section
            HeaderSection(
                chainsCount = uiState.chains.size,
                averageStreak = uiState.averageStreak
            )

            // Weekly Heatmap
            HeatmapCard(heatmapDays = uiState.heatmap)

            // Nudge alerts
            if (uiState.nudgeChains.isNotEmpty()) {
                NudgeBanner(atRiskChains = uiState.nudgeChains)
            }

            // Divider or Section Label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Habit Stacks",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${uiState.chains.size} Active",
                    color = MaterialTheme.colorScheme.primary,
                    style = TextStyle(fontSize = 13.sp)
                )
            }

            // Chains List
            if (uiState.chains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No stacks yet. Tap + to build your first micro-habit stack!",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        style = TextStyle(fontSize = 14.sp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.chains, key = { it.chain.id }) { chainDetails ->
                        ChainCard(
                            chainDetails = chainDetails,
                            onToggleStep = { stepId, isCompleted ->
                                viewModel.toggleStep(chainDetails.chain.id, stepId, isCompleted)
                            },
                            onEdit = {
                                editingChain = chainDetails
                            },
                            onDelete = {
                                viewModel.deleteChain(chainDetails.chain)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Stack Dialog
    if (showAddDialog) {
        AddEditStackDialog(
            title = "Create New Habit Stack",
            onDismiss = { showAddDialog = false },
            onSave = { name, anchor, steps ->
                viewModel.createChain(name, anchor, steps)
                showAddDialog = false
            }
        )
    }

    // Edit Stack Dialog
    if (editingChain != null) {
        val details = editingChain!!
        AddEditStackDialog(
            title = "Edit Habit Stack",
            initialName = details.chain.name,
            initialAnchor = details.chain.anchor,
            initialSteps = details.steps.map { it.action },
            onDismiss = { editingChain = null },
            onSave = { name, anchor, steps ->
                viewModel.updateChain(details.chain.id, name, anchor, steps)
                editingChain = null
            }
        )
    }
}

// --- SUBVIEWS ---

@Composable
fun HeaderSection(
    chainsCount: Int,
    averageStreak: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "StackUp",
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = "Habit & Goal Stacker",
                    color = MaterialTheme.colorScheme.primary,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Total Stacks
                CardColumnStat(label = "Stacks", value = chainsCount.toString())
                // Avg Streak
                CardColumnStat(
                    label = "Avg Streak",
                    value = "🔥 $averageStreak",
                    valueColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun CardColumnStat(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = valueColor,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun HeatmapCard(
    heatmapDays: List<HeatmapDay>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Heatmap",
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Compliance Strength",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                heatmapDays.forEach { day ->
                    val color = when {
                        day.completionRatio == 0f -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        day.completionRatio < 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        day.completionRatio < 1.0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else -> MaterialTheme.colorScheme.secondary // Solid Gold for flawless 100% complete stack!
                    }

                    val borderSpec = if (day.date == LocalDate.now()) {
                        BorderStroke(2.dp, Color.White) // Target today with a bold halo
                    } else {
                        null
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = color,
                            border = borderSpec
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (day.completionRatio == 1.0f) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Perfect Stack",
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = day.dayOfWeekLabel,
                            color = if (day.date == LocalDate.now()) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = if (day.date == LocalDate.now()) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NudgeBanner(
    atRiskChains: List<ChainWithDetails>,
    modifier: Modifier = Modifier
) {
    // Elegant pulsing animation to warning nudge
    val infiniteTransition = rememberInfiniteTransition(label = "nudge_pulse")
    val bannerScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "banner_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .scale(bannerScale)
            .testTag("nudge_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.error, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Risk warning",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "⚠️ Break the Chain Warning!",
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                val riskDescription = atRiskChains.joinToString(", ") { "${it.chain.name} (🔥 ${it.streak}d)" }
                Text(
                    text = "Don't break the chain of: $riskDescription! Complete today's stack steps.",
                    color = SoftIce,
                    style = TextStyle(fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
fun ChainCard(
    chainDetails: ChainWithDetails,
    onToggleStep: (Int, Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chain = chainDetails.chain
    val steps = chainDetails.steps

    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chain_card_${chain.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (chainDetails.isFullyCompletedToday) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (chainDetails.isFullyCompletedToday) 2.dp else 1.dp,
            color = if (chainDetails.isFullyCompletedToday) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Anchor Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chain.name,
                            color = Color.White,
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chainDetails.streak > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "🔥 ${chainDetails.streak}d",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚓ Anchor Event: ",
                            color = MaterialTheme.colorScheme.primary,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = chain.anchor,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Stack",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Stack",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Checkoff Sequence Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Stack Links — ${chainDetails.completionsToday.size}/${steps.size}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (chainDetails.isFullyCompletedToday) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Complete Stack Badge",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "STAKULINK SECURED!",
                                color = MaterialTheme.colorScheme.secondary,
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    steps.forEachIndexed { idx, step ->
                        val isCompleted = chainDetails.completionsToday.contains(step.id)
                        
                        // Check if the next step is also completed to activate glowing wire links!
                        val nextCompleted = if (idx < steps.lastIndex) {
                            chainDetails.completionsToday.contains(steps[idx + 1].id)
                        } else {
                            false
                        }

                        val stepActiveChainGlow = isCompleted && nextCompleted

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Link columns drawn behind with Canvas
                            val colorPrimary = MaterialTheme.colorScheme.primary
                            val colorMuted = MaterialTheme.colorScheme.surfaceVariant
                            val drawDensity = LocalDensity.current

                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .fillMaxHeight()
                                    .drawBehind {
                                        // Draw the wire link graphic connecting to the succeeding micro-habit
                                        if (idx < steps.lastIndex) {
                                            val strokePx = if (stepActiveChainGlow) 4.5.dp.toPx() else 2.dp.toPx()
                                            val strokeColor = if (stepActiveChainGlow) colorPrimary else colorMuted
                                            val dashEffect = if (stepActiveChainGlow) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                                            drawLine(
                                                color = strokeColor,
                                                start = Offset(size.width / 2, size.height / 2),
                                                end = Offset(size.width / 2, size.height),
                                                strokeWidth = strokePx,
                                                pathEffect = dashEffect
                                            )
                                        }

                                        // Draw the preceding connection logic wire
                                        if (idx > 0) {
                                            val prevCompleted = chainDetails.completionsToday.contains(steps[idx - 1].id)
                                            val prevActiveGlow = isCompleted && prevCompleted
                                            val strokePx = if (prevActiveGlow) 4.5.dp.toPx() else 2.dp.toPx()
                                            val strokeColor = if (prevActiveGlow) colorPrimary else colorMuted
                                            val dashEffect = if (prevActiveGlow) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                                            drawLine(
                                                color = strokeColor,
                                                start = Offset(size.width / 2, 0f),
                                                end = Offset(size.width / 2, size.height / 2),
                                                strokeWidth = strokePx,
                                                pathEffect = dashEffect
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CheckoffCircle(
                                    isCompleted = isCompleted,
                                    onToggle = {
                                        onToggleStep(step.id, !isCompleted)
                                    }
                                )
                            }

                            // Step Text Action details
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = step.action,
                                    color = if (isCompleted) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else Color.White,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = if (isCompleted) FontWeight.Medium else FontWeight.Bold,
                                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                )
                                Text(
                                    text = "Step ${idx + 1} attached",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    style = TextStyle(fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoffCircle(
    isCompleted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var triggerCount by remember { mutableStateOf(0) }
    
    // Spring action mechanical bounce
    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .clickable(
                onClick = {
                    if (!isCompleted) {
                        triggerCount++
                    }
                    onToggle()
                }
            )
    ) {
        // Sparkle Particles Burst Emitter
        ParticleBurstEffect(
            modifier = Modifier.fillMaxSize(),
            triggerCount = triggerCount,
            color = MaterialTheme.colorScheme.primary
        )

        // The Checkmark Circle
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Step Complete",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Transparent, shape = CircleShape)
                    .border(2.5.dp, MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
            )
        }
    }
}

@Composable
fun ParticleBurstEffect(
    modifier: Modifier = Modifier,
    triggerCount: Int,
    color: Color
) {
    var progress by remember { mutableStateOf(0f) }
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "burst_progress"
    )

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            progress = 0f
            progress = 1f
        }
    }

    if (animProgress > 0f && animProgress < 1f) {
        Canvas(modifier = modifier) {
            val center = size.width / 2
            val maxRadius = size.width * 1.5f
            val count = 8
            for (i in 0 until count) {
                val angle = (2 * Math.PI * i / count).toFloat()
                val currentRadius = maxRadius * animProgress
                val x = center + currentRadius * kotlin.math.cos(angle)
                val y = center + currentRadius * kotlin.math.sin(angle)
                
                val opacity = 1f - animProgress
                val radiusPx = (4.dp.toPx()) * (1f - animProgress)
                
                drawCircle(
                    color = color.copy(alpha = opacity),
                    radius = radiusPx,
                    center = Offset(x, y)
                )
            }
        }
    }
}

// --- CREATION / EDIT MODAL ---

@Composable
fun AddEditStackDialog(
    title: String,
    initialName: String = "",
    initialAnchor: String = "",
    initialSteps: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var anchor by remember { mutableStateOf(initialAnchor) }
    var stepsList = remember { mutableStateListOf<String>().apply { 
        if (initialSteps.isEmpty()) {
            add("") // default first blank line
            add("") // default second blank line
        } else {
            addAll(initialSteps)
        }
    }}

    var nameError by remember { mutableStateOf(false) }
    var anchorError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("add_edit_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name
                    Text(
                        text = "Stack Card Label",
                        color = Color.White,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            nameError = false 
                        },
                        placeholder = { Text("e.g. Work Morning Ignition", style = TextStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))) },
                        isError = nameError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Anchor
                    Text(
                        text = "Anchor Event Trigger (Real-life cue)",
                        color = Color.White,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = anchor,
                        onValueChange = { 
                            anchor = it
                            anchorError = false 
                        },
                        placeholder = { Text("e.g. Brew morning coffee", style = TextStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))) },
                        isError = anchorError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_anchor_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Steps Label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stacked Micro-Habits (In Sequence)",
                            color = Color.White,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                        Button(
                            onClick = { stepsList.add("") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+ Add Link Step", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Steps Item Builders
                items(stepsList.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedTextField(
                            value = stepsList[index],
                            onValueChange = { stepsList[index] = it },
                            placeholder = { Text("Habit step action details...", fontSize = 13.sp, style = TextStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_step_input_$index"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (stepsList.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { stepsList.removeAt(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Step",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    nameError = true
                                }
                                if (anchor.isBlank()) {
                                    anchorError = true
                                }
                                if (name.isNotBlank() && anchor.isNotBlank() && stepsList.any { it.isNotBlank() }) {
                                    onSave(name, anchor, stepsList.filter { it.isNotBlank() })
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("dialog_save_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Stack", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
