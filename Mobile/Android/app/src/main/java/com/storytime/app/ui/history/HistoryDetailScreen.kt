package com.storytime.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                        }
                    }
                }
            }
        }
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
