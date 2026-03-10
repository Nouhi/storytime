package com.storytime.app.ui.generate

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.storytime.app.model.FamilyMemberResponse
import com.storytime.app.model.StoryPage
import com.storytime.app.model.StyleItem
import com.storytime.app.audio.AmbientSound
import com.storytime.app.StorytimeApp
import com.storytime.app.ui.bedtime.BedtimeScreen
import com.storytime.app.ui.settings.FamilyMembersViewModel
import com.storytime.app.ui.components.PageNavigationOverlay
import com.storytime.app.ui.components.PageReader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(viewModel: GenerateViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val showBedtime by viewModel.showBedtime.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadStyles() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    // Reset sleep timer counter when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.resetSleepTimerCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBedtime) {
        val app = context.applicationContext as StorytimeApp
        val bedtimeSoundKey by app.preferencesManager.bedtimeSound.collectAsState(initial = "whiteNoise")
        val coroutineScope = rememberCoroutineScope()
        BedtimeScreen(
            audioManager = app.audioManager,
            initialSound = AmbientSound.fromKey(bedtimeSoundKey),
            onSoundChanged = { key ->
                coroutineScope.launch { app.preferencesManager.setBedtimeSound(key) }
            },
            onExit = { viewModel.deactivateBedtimeMode() }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Storytime") })
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    GenerationState.IDLE -> IdleView(viewModel)
                    GenerationState.GENERATING -> GeneratingView(viewModel)
                    GenerationState.COMPLETE -> CompleteView(viewModel)
                    GenerationState.ERROR -> ErrorView(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleView(viewModel: GenerateViewModel) {
    val prompt by viewModel.prompt.collectAsState()
    val writingStyle by viewModel.writingStyle.collectAsState()
    val imageStyle by viewModel.imageStyle.collectAsState()
    val writingStyles by viewModel.writingStyles.collectAsState()
    val imageStyles by viewModel.imageStyles.collectAsState()
    val kidName by viewModel.kidName.collectAsState()
    val familyMembers by viewModel.familyMembers.collectAsState()
    val selectedCharacterIds by viewModel.selectedCharacterIds.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Greeting
        Text(
            viewModel.greeting,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Setup prompt when no kid name
        if (kidName.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = Color(0xFFFF9800))
                    Text(
                        "Set up your child's name in Settings for personalized stories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Prompt input
        Text("What story shall we create?", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = prompt,
            onValueChange = { viewModel.updatePrompt(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            placeholder = { Text("Describe your story idea...") }
        )

        // Suggestion chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.suggestions) { suggestion ->
                SuggestionChip(
                    onClick = { viewModel.updatePrompt(suggestion) },
                    label = { Text(suggestion, maxLines = 1) }
                )
            }
        }

        // Style pickers
        if (writingStyles.isNotEmpty()) {
            StylePicker(
                title = "Writing Style",
                styles = writingStyles,
                selected = writingStyle,
                onSelect = { viewModel.updateWritingStyle(it) }
            )
        }

        if (imageStyles.isNotEmpty()) {
            StylePicker(
                title = "Image Style",
                styles = imageStyles,
                selected = imageStyle,
                onSelect = { viewModel.updateImageStyle(it) }
            )
        }

        // Character picker
        if (familyMembers.isNotEmpty()) {
            CharacterPicker(
                members = familyMembers,
                selectedIds = selectedCharacterIds,
                onToggle = { viewModel.toggleCharacter(it) },
                onSelectAll = { viewModel.selectAllCharacters() },
                onClearAll = { viewModel.clearCharacterSelection() }
            )
        }

        // Generate button
        Button(
            onClick = { viewModel.generate() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = prompt.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Story", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StylePicker(
    title: String,
    styles: List<StyleItem>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(styles) { style ->
                val isSelected = style.id == selected
                Surface(
                    onClick = { onSelect(style.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(
                        2.dp, MaterialTheme.colorScheme.primary
                    ) else null,
                    modifier = Modifier.size(width = 72.dp, height = 64.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(style.emoji, fontSize = 20.sp)
                        Text(
                            style.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterPicker(
    members: List<FamilyMemberResponse>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Characters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (selectedIds.size == members.size) "All included"
                    else "${selectedIds.size} of ${members.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (selectedIds.size == members.size) {
                TextButton(onClick = onClearAll) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                TextButton(onClick = onSelectAll) {
                    Text("Select All", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            members.forEach { member ->
                val isSelected = selectedIds.contains(member.id)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(member.id) },
                    label = { Text(member.name) },
                    leadingIcon = {
                        Text(FamilyMembersViewModel.roleEmoji(member.role))
                    }
                )
            }
        }
    }
}

@Composable
private fun GeneratingView(viewModel: GenerateViewModel) {
    val progress by viewModel.progress.collectAsState()
    val stepDetail by viewModel.stepDetail.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()

    // Smooth percentage animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat(),
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "progress"
    )

    // Continuous animation driver (0..1 looping)
    val infiniteTransition = rememberInfiniteTransition(label = "gen_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated illustration area
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            when (currentStep) {
                "generating-story" -> PenWritingAnimation(animPhase, primaryColor)
                "generating-images" -> BrushPaintingAnimation(animPhase, secondaryColor, primaryColor)
                "assembling-ebook" -> BookAssemblyAnimation(animPhase, primaryColor)
                else -> StarSparkleAnimation(animPhase, primaryColor)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Step label
        val stepLabel = when (currentStep) {
            "generating-story" -> "Writing your story..."
            "generating-images" -> "Painting illustrations..."
            "assembling-ebook" -> "Assembling your book..."
            else -> "Preparing..."
        }
        Text(
            stepLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            stepDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { animatedProgress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            trackColor = surfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        // Smooth percentage text
        Text(
            "${animatedProgress.toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
}

// --- Pen writing on an open book ---
@Composable
private fun PenWritingAnimation(phase: Float, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Draw open book
        val bookW = w * 0.75f
        val bookH = h * 0.55f
        val bookLeft = cx - bookW / 2f
        val bookTop = cy - bookH / 2f + 10f

        // Left page
        drawRoundRect(
            color = Color(0xFFFFF8E1),
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookW / 2f - 2f, bookH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Right page
        drawRoundRect(
            color = Color(0xFFFFF8E1),
            topLeft = Offset(cx + 2f, bookTop),
            size = Size(bookW / 2f - 2f, bookH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Spine line
        drawLine(
            color = Color(0xFFD7CCC8),
            start = Offset(cx, bookTop),
            end = Offset(cx, bookTop + bookH),
            strokeWidth = 2f
        )
        // Book outline
        drawRoundRect(
            color = Color(0xFFBCAAA4),
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookW, bookH),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 2f)
        )

        // Animated text lines on left page
        val lineStartX = bookLeft + 12f
        val lineEndX = cx - 12f
        val lineY0 = bookTop + 18f
        val lineSpacing = 14f
        val totalLines = 6

        for (i in 0 until totalLines) {
            val lineProgress = ((phase * (totalLines + 1)) - i).coerceIn(0f, 1f)
            if (lineProgress > 0f) {
                val endX = lineStartX + (lineEndX - lineStartX) * lineProgress
                drawLine(
                    color = Color(0xFF5D4037).copy(alpha = 0.6f),
                    start = Offset(lineStartX, lineY0 + i * lineSpacing),
                    end = Offset(endX, lineY0 + i * lineSpacing),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Lines on right page (fill with phase offset)
        val rLineStartX = cx + 12f
        val rLineEndX = cx + bookW / 2f - 12f
        for (i in 0 until totalLines) {
            val rProgress = ((phase * (totalLines + 1)) - i - 0.5f).coerceIn(0f, 1f)
            if (rProgress > 0f) {
                val endX = rLineStartX + (rLineEndX - rLineStartX) * rProgress * 0.7f
                drawLine(
                    color = Color(0xFF5D4037).copy(alpha = 0.4f),
                    start = Offset(rLineStartX, lineY0 + i * lineSpacing),
                    end = Offset(endX, lineY0 + i * lineSpacing),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Animated pen
        val penLineIdx = (phase * (totalLines + 1)).toInt().coerceAtMost(totalLines - 1)
        val penFrac = ((phase * (totalLines + 1)) - penLineIdx).coerceIn(0f, 1f)
        val penX = lineStartX + (lineEndX - lineStartX) * penFrac
        val penY = lineY0 + penLineIdx * lineSpacing

        drawPen(penX, penY, color, phase)
    }
}

private fun DrawScope.drawPen(tipX: Float, tipY: Float, color: Color, phase: Float) {
    val penLength = 50f
    val wobble = sin(phase * Math.PI * 6).toFloat() * 2f

    // Pen body (angled)
    val angle = -45f
    rotate(degrees = angle, pivot = Offset(tipX, tipY)) {
        // Pen barrel
        drawRoundRect(
            color = color,
            topLeft = Offset(tipX - 3f, tipY - penLength),
            size = Size(6f, penLength - 8f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        // Pen nib
        val nibPath = Path().apply {
            moveTo(tipX - 3f, tipY - 8f)
            lineTo(tipX + 3f, tipY - 8f)
            lineTo(tipX + wobble, tipY)
            close()
        }
        drawPath(nibPath, color = Color(0xFF37474F))
        // Gold band
        drawRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(tipX - 3.5f, tipY - penLength + 4f),
            size = Size(7f, 5f)
        )
    }
}

// --- Brush painting with color strokes ---
@Composable
private fun BrushPaintingAnimation(phase: Float, brushColor: Color, accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Canvas/easel background
        val canvasW = w * 0.65f
        val canvasH = h * 0.55f
        val canvasLeft = cx - canvasW / 2f
        val canvasTop = cy - canvasH / 2f + 10f

        // Easel legs
        drawLine(
            color = Color(0xFF8D6E63),
            start = Offset(canvasLeft + 15f, canvasTop + canvasH),
            end = Offset(canvasLeft - 5f, canvasTop + canvasH + 35f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF8D6E63),
            start = Offset(canvasLeft + canvasW - 15f, canvasTop + canvasH),
            end = Offset(canvasLeft + canvasW + 5f, canvasTop + canvasH + 35f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Canvas
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(canvasLeft, canvasTop),
            size = Size(canvasW, canvasH),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0xFFBDBDBD),
            topLeft = Offset(canvasLeft, canvasTop),
            size = Size(canvasW, canvasH),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 2f)
        )

        // Paint strokes appearing progressively
        val colors = listOf(
            Color(0xFF42A5F5), // blue
            Color(0xFF66BB6A), // green
            Color(0xFFFF7043), // orange
            Color(0xFFAB47BC), // purple
            Color(0xFFFFCA28)  // yellow
        )

        val strokes = listOf(
            Triple(0.15f, 0.25f, 0.3f),  // startX frac, startY frac, width frac
            Triple(0.5f, 0.2f, 0.35f),
            Triple(0.1f, 0.5f, 0.4f),
            Triple(0.45f, 0.55f, 0.4f),
            Triple(0.2f, 0.75f, 0.5f)
        )

        for (i in strokes.indices) {
            val strokeProgress = ((phase * (strokes.size + 1)) - i).coerceIn(0f, 1f)
            if (strokeProgress > 0f) {
                val (sx, sy, sw) = strokes[i]
                val startX = canvasLeft + canvasW * sx
                val startY = canvasTop + canvasH * sy
                val strokeW = canvasW * sw * strokeProgress

                val path = Path().apply {
                    moveTo(startX, startY)
                    val cp1x = startX + strokeW * 0.3f
                    val cp1y = startY + sin(i * 1.5) .toFloat() * 12f
                    val cp2x = startX + strokeW * 0.7f
                    val cp2y = startY - sin(i * 2.0).toFloat() * 8f
                    cubicTo(cp1x, cp1y, cp2x, cp2y, startX + strokeW, startY + sin(i * 0.7).toFloat() * 5f)
                }

                drawPath(
                    path,
                    color = colors[i % colors.size].copy(alpha = 0.7f),
                    style = Stroke(
                        width = 8f + (i % 3) * 3f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Animated paintbrush
        val brushIdx = (phase * (strokes.size + 1)).toInt().coerceAtMost(strokes.size - 1)
        val brushFrac = ((phase * (strokes.size + 1)) - brushIdx).coerceIn(0f, 1f)
        val (bsx, bsy, bsw) = strokes[brushIdx]
        val brushX = canvasLeft + canvasW * bsx + canvasW * bsw * brushFrac
        val brushY = canvasTop + canvasH * bsy + sin(brushIdx * 0.7).toFloat() * 5f * brushFrac

        drawBrush(brushX, brushY, colors[brushIdx % colors.size], brushColor)
    }
}

private fun DrawScope.drawBrush(tipX: Float, tipY: Float, bristleColor: Color, handleColor: Color) {
    val handleLen = 55f
    rotate(degrees = -30f, pivot = Offset(tipX, tipY)) {
        // Handle
        drawRoundRect(
            color = handleColor,
            topLeft = Offset(tipX - 4f, tipY - handleLen),
            size = Size(8f, handleLen - 12f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        // Ferrule (metal band)
        drawRect(
            color = Color(0xFFBDBDBD),
            topLeft = Offset(tipX - 5f, tipY - 14f),
            size = Size(10f, 6f)
        )
        // Bristles
        drawRoundRect(
            color = bristleColor.copy(alpha = 0.8f),
            topLeft = Offset(tipX - 4f, tipY - 8f),
            size = Size(8f, 10f),
            cornerRadius = CornerRadius(1f, 4f)
        )
    }
}

// --- Book assembly animation ---
@Composable
private fun BookAssemblyAnimation(phase: Float, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "book_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "book_scale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Book
        val bookW = 80f * pulse
        val bookH = 100f * pulse

        // Back cover
        drawRoundRect(
            color = color.copy(alpha = 0.3f),
            topLeft = Offset(cx - bookW / 2f + 6f, cy - bookH / 2f + 4f),
            size = Size(bookW, bookH),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Pages
        drawRoundRect(
            color = Color(0xFFFFF8E1),
            topLeft = Offset(cx - bookW / 2f + 3f, cy - bookH / 2f + 2f),
            size = Size(bookW - 4f, bookH - 4f),
            cornerRadius = CornerRadius(2f, 4f)
        )

        // Front cover
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - bookW / 2f, cy - bookH / 2f),
            size = Size(bookW, bookH),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Star on cover
        val starCx = cx
        val starCy = cy - 10f
        drawStar(starCx, starCy, 14f * pulse, Color.White)

        // Sparkles around book
        val sparkleCount = 6
        for (i in 0 until sparkleCount) {
            val angle = (i.toFloat() / sparkleCount) * 360f + phase * 360f
            val rad = Math.toRadians(angle.toDouble())
            val dist = 65f + sin(phase * Math.PI * 2 + i).toFloat() * 10f
            val sx = cx + (kotlin.math.cos(rad) * dist).toFloat()
            val sy = cy + (kotlin.math.sin(rad) * dist).toFloat()
            val sparkleSize = 4f + sin(phase * Math.PI * 3 + i * 2).toFloat() * 3f

            drawStar(sx, sy, sparkleSize, color.copy(alpha = 0.6f))
        }
    }
}

private fun DrawScope.drawStar(cx: Float, cy: Float, radius: Float, color: Color) {
    // 4-pointed star
    val path = Path().apply {
        moveTo(cx, cy - radius)
        lineTo(cx + radius * 0.3f, cy - radius * 0.3f)
        lineTo(cx + radius, cy)
        lineTo(cx + radius * 0.3f, cy + radius * 0.3f)
        lineTo(cx, cy + radius)
        lineTo(cx - radius * 0.3f, cy + radius * 0.3f)
        lineTo(cx - radius, cy)
        lineTo(cx - radius * 0.3f, cy - radius * 0.3f)
        close()
    }
    drawPath(path, color)
}

// --- Sparkle animation for initial state ---
@Composable
private fun StarSparkleAnimation(phase: Float, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Central sparkle
        val mainSize = 24f + sin(phase * Math.PI * 2).toFloat() * 8f
        drawStar(cx, cy, mainSize, color)

        // Orbiting sparkles
        for (i in 0 until 4) {
            val angle = (i * 90f) + phase * 360f
            val rad = Math.toRadians(angle.toDouble())
            val dist = 50f
            val sx = cx + (kotlin.math.cos(rad) * dist).toFloat()
            val sy = cy + (kotlin.math.sin(rad) * dist).toFloat()
            val sparkleSize = 8f + sin(phase * Math.PI * 4 + i).toFloat() * 4f
            drawStar(sx, sy, sparkleSize, color.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CompleteView(viewModel: GenerateViewModel) {
    val storyPages by viewModel.storyPages.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val hasImages by viewModel.hasImages.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Page reader
        Box(modifier = Modifier.weight(1f)) {
            PageReader(
                currentPage = currentPage,
                totalPages = storyPages.size,
                onPageChanged = { viewModel.updateCurrentPage(it) }
            ) { pageNum ->
                val page = storyPages.firstOrNull { it.page == pageNum }
                if (page != null) {
                    StoryPageContent(
                        page = page,
                        imageUrl = if (hasImages) viewModel.pageImageUrl(pageNum) else null
                    )
                }
            }

            PageNavigationOverlay(
                currentPage = currentPage,
                totalPages = storyPages.size,
                onPrevious = { viewModel.updateCurrentPage(currentPage - 1) },
                onNext = { viewModel.updateCurrentPage(currentPage + 1) }
            )
        }

        // Bottom bar
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Page $currentPage of ${storyPages.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.saveEpub() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Books")
                    }

                    Button(
                        onClick = { viewModel.savePdf() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }

                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }

                    Button(
                        onClick = { viewModel.activateBedtimeMode() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB))
                    ) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sleep")
                    }
                }
            }
        }
    }
}

@Composable
fun StoryPageContent(
    page: StoryPage,
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (page.page) {
            1 -> CoverLayout(page, imageUrl)
            16 -> ClosingLayout(page, imageUrl)
            else -> BodyLayout(page, imageUrl)
        }
    }
}

@Composable
private fun CoverLayout(page: StoryPage, imageUrl: String?) {
    if (imageUrl != null) {
        StoryImage(url = imageUrl, heightFraction = 0.55f)
    }

    Spacer(Modifier.height(16.dp))

    // Split title and subtitle
    val titleRegex = Regex("""(\s*[-:,]?\s*[Aa] bedtime story.*)""")
    val match = titleRegex.find(page.text)

    if (match != null) {
        val mainTitle = page.text.substring(0, match.range.first)
        val subtitle = match.value.trim().trimStart('-', ':', ',', ' ')

        Text(
            mainTitle,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            page.text,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }

    Spacer(Modifier.height(16.dp))
}

@Composable
private fun BodyLayout(page: StoryPage, imageUrl: String?) {
    if (imageUrl != null) {
        Spacer(Modifier.height(8.dp))
        StoryImage(url = imageUrl, heightFraction = 0.5f)
    }

    Spacer(Modifier.height(12.dp))

    Text(
        page.text,
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        lineHeight = 28.sp,
        modifier = Modifier.padding(horizontal = 12.dp)
    )

    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ClosingLayout(page: StoryPage, imageUrl: String?) {
    if (imageUrl != null) {
        StoryImage(url = imageUrl, heightFraction = 0.5f)
    }

    Spacer(Modifier.height(20.dp))

    Text(
        page.text,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        lineHeight = 30.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(Modifier.height(20.dp))
}

@Composable
private fun StoryImage(url: String, heightFraction: Float) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Story illustration",
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        },
        error = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = "Image failed to load",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun ErrorView(viewModel: GenerateViewModel) {
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(16.dp))

        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(8.dp))

        Text(
            errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = { viewModel.generate() }) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try Again")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { viewModel.reset() }) {
            Text("Start Over")
        }
    }
}
