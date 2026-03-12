package com.storytime.app.ui.history

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storytime.app.R
import com.storytime.app.ui.components.PageNavigationOverlay
import com.storytime.app.ui.components.PageReader
import com.storytime.app.ui.generate.StoryPageContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    storyId: Int,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val storyPages by viewModel.storyPages.collectAsState()
    val storyTitle by viewModel.storyTitle.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val stories by viewModel.stories.collectAsState()

    val story = stories.firstOrNull { it.id == storyId }
    val hasImages = (story?.geminiImageCount ?: 0) > 0

    val bookletState by viewModel.bookletState.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(storyId) {
        if (stories.isEmpty()) viewModel.loadHistory()
        viewModel.loadStoryDetail(storyId)
    }

    // Clean title for top bar
    val cleanTitle = storyTitle
        .replace(Regex("""\s*[-:,]?\s*[Aa] bedtime story.*$"""), "")
        .ifEmpty { storyTitle }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        cleanTitle,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (isLoadingDetail) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (storyPages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.story_not_found), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
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
                                imageUrl = if (hasImages) viewModel.pageImageUrl(storyId, pageNum) else null
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
                            stringResource(R.string.page_indicator, currentPage, storyPages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.downloadEpub(storyId, storyTitle)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.button_books))
                            }

                            Button(
                                onClick = {
                                    viewModel.downloadPdf(storyId, storyTitle)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                )
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.button_pdf))
                            }

                            Button(
                                onClick = {
                                    viewModel.downloadBookletPdf(storyId, storyTitle)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.button_booklet))
                            }
                        }
                    }
                }
            }
        }
    }

    // Booklet download overlay
    if (bookletState !is HistoryViewModel.BookletState.Idle) {
        BookletDownloadOverlay(
            state = bookletState,
            onDismiss = { viewModel.dismissBookletOverlay() }
        )
    }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_story_title)) },
            text = { Text(stringResource(R.string.delete_story_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStory(storyId)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.button_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }
}

@Composable
private fun BookletDownloadOverlay(
    state: HistoryViewModel.BookletState,
    onDismiss: () -> Unit
) {
    val isDownloading = state is HistoryViewModel.BookletState.Downloading
    val doneState = state as? HistoryViewModel.BookletState.Done

    val infiniteTransition = rememberInfiniteTransition(label = "booklet")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )
    val dots = ".".repeat(dotCount.toInt().coerceIn(0, 3))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.padding(48.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                if (isDownloading) {
                    PrintingAnimation()

                    Text(
                        "Preparing your booklet$dots",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                    )

                    Text(
                        "Your story is being formatted\nfor printing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                } else if (doneState != null) {
                    Icon(
                        if (doneState.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = if (doneState.isError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        doneState.message,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (doneState.isError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        )
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "print")

    // Page slides down from printer (cycles 0→1 repeatedly)
    val pageCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "pageCycle"
    )

    // Printer subtle pulse synced with page ejection
    val printerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "printerScale"
    )

    // Second page offset for stacking feel
    val stackCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stackCycle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(width = 100.dp, height = 120.dp)
    ) {
        // Stacked printed pages below
        val stackPages = (stackCycle * 3).toInt().coerceIn(0, 2)
        for (i in 0..stackPages) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .size(width = (36 - i * 2).dp, height = (44 - i * 2).dp)
                    .offset(y = (38 + i * 3).dp)
            ) {}
        }

        // Page sliding out of printer
        val pageY = -30f + (pageCycle * 60f) // -30 → +30
        val pageAlpha = if (pageCycle < 0.1f) pageCycle * 10f
                        else if (pageCycle > 0.85f) (1f - pageCycle) * 6.67f
                        else 1f

        Surface(
            shape = RoundedCornerShape(2.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(width = 32.dp, height = 40.dp)
                .offset(y = pageY.dp)
                .graphicsLayer(alpha = pageAlpha.coerceIn(0f, 1f))
        ) {
            // Text lines on the page
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(6.dp)
            ) {
                repeat(4) {
                    Surface(
                        shape = RoundedCornerShape(0.5.dp),
                        color = Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    ) {}
                }
            }
        }

        // Printer icon on top
        Icon(
            Icons.Default.Print,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer(scaleX = printerScale, scaleY = printerScale),
            tint = Color(0xFF4CAF50)
        )
    }
}
