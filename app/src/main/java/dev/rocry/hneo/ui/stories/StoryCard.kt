package dev.rocry.hneo.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.rocry.hneo.data.OpenGraphService
import dev.rocry.hneo.model.Story
import dev.rocry.hneo.ui.components.einkClickable
import dev.rocry.hneo.ui.theme.LocalEinkMode
import dev.rocry.hneo.ui.theme.storyAccentColor
import kotlinx.coroutines.launch

@Composable
fun StoryCard(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val einkMode = LocalEinkMode.current
    var thumbnailUrl by remember(story.url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Skip thumbnail fetching in e-ink mode
    if (!einkMode) {
        LaunchedEffect(story.url) {
            story.url?.let { url ->
                scope.launch {
                    thumbnailUrl = OpenGraphService.fetchOgImage(url)
                }
            }
        }
    }

    val accentColor = storyAccentColor(story.points, story.commentsCount)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .einkClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            val meta = buildString {
                story.domain?.let { append("$it  ·  ") }
                story.points?.let { append("$it pts  ·  ") }
                if (story.commentsCount > 0) append("${story.commentsCount} comments  ·  ")
                append(story.timeAgo)
            }

            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // No thumbnails in e-ink mode
        if (!einkMode && thumbnailUrl != null) {
            Spacer(modifier = Modifier.width(12.dp))
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
