package dev.rocry.hneo.ui.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.rocry.hneo.data.OpenGraphService
import dev.rocry.hneo.model.Story
import kotlinx.coroutines.launch

@Composable
fun StoryHeader(story: Story, modifier: Modifier = Modifier) {
    var thumbnailUrl by remember(story.url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(story.url) {
        story.url?.let { url ->
            scope.launch {
                thumbnailUrl = OpenGraphService.fetchOgImage(url)
            }
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                story.domain?.let { domain ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (thumbnailUrl != null) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val meta = buildString {
            story.points?.let { append("$it pts  ·  ") }
            if (story.commentsCount > 0) append("${story.commentsCount} comments  ·  ")
            story.user?.let { append("$it  ·  ") }
            append(story.timeAgo)
        }

        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
