package com.storytime.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storytime.app.model.StoryHistoryEntry
import com.storytime.app.ui.theme.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    onStoryClick: (Int) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val stories by viewModel.stories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var storyToDelete by remember { mutableStateOf<StoryHistoryEntry?>(null) }

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("History") })
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple500)
            }
        } else if (stories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No stories yet", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your stories will appear here once you create one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(stories, key = { it.id }) { story ->
                    StoryCard(
                        story = story,
                        onClick = { onStoryClick(story.id) },
                        onDelete = { storyToDelete = story }
                    )
                }
            }
        }
    }

    // Delete confirmation
    storyToDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            title = { Text("Delete Story") },
            text = { Text("Are you sure you want to delete this story? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStory(story.id)
                        storyToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { storyToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StoryCard(
    story: StoryHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Story thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Purple80, Purple500.copy(alpha = 0.2f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = Purple500.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title (cleaned)
                val cleanTitle = story.title
                    .replace(Regex("""\s*[-:,]?\s*[Aa] bedtime story.*$"""), "")
                    .ifEmpty { story.title }

                Text(
                    cleanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Prompt
                Text(
                    story.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Relative date
                Text(
                    relativeDate(story.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun relativeDate(isoString: String): String {
    return try {
        val parsed = ZonedDateTime.parse(isoString)
        val now = ZonedDateTime.now()
        val days = ChronoUnit.DAYS.between(parsed.toLocalDate(), now.toLocalDate())
        when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            else -> parsed.toLocalDate().toString()
        }
    } catch (_: Exception) {
        isoString.take(10)
    }
}
