package com.example.feedsense.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.feedsense.ui.theme.Background
import com.example.feedsense.ui.theme.Border
import com.example.feedsense.ui.theme.SurfaceElevated
import com.example.feedsense.ui.theme.SurfaceVariant

// --------------------------------
// FEEDSENSE CARD
// --------------------------------
//
// Milestone 8A. Consistent card surface used across
// every screen. Elevated variant for emphasis.
//

@Composable
fun FeedSenseCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = if (elevated) {
        SurfaceElevated
    } else {
        MaterialTheme.colorScheme.surface
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    Border
                )
            ),
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    Border
                )
            ),
            content = content
        )
    }
}

// --------------------------------
// SECTION HEADER
// --------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --------------------------------
// METRIC CARD
// --------------------------------
//
// Milestone 8D/8M. Compact stat display.
//

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    FeedSenseCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

// --------------------------------
// STATUS CHIP
// --------------------------------
//
// Milestone 8P. Active/inactive status indicator.
//

@Composable
fun StatusChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) {
            com.example.feedsense.ui.theme.StatusActive.copy(alpha = 0.15f)
        } else {
            com.example.feedsense.ui.theme.StatusInactive.copy(alpha = 0.15f)
        },
        animationSpec = tween(300),
        label = "status_chip"
    )

    val textColor by animateColorAsState(
        targetValue = if (active) {
            com.example.feedsense.ui.theme.StatusActive
        } else {
            com.example.feedsense.ui.theme.StatusInactive
        },
        animationSpec = tween(300),
        label = "status_text"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// --------------------------------
// CONFIDENCE BADGE
// --------------------------------
//
// Milestone 8A/8I. Color-coded confidence indicator.
//

@Composable
fun ConfidenceBadge(
    confidence: Double?,
    modifier: Modifier = Modifier
) {
    val displayText = if (confidence != null) {
        "${(confidence * 100).toInt()}%"
    } else {
        "N/A"
    }

    val color = when {
        confidence == null -> com.example.feedsense.ui.theme.ConfidenceLow
        confidence >= 0.8 -> com.example.feedsense.ui.theme.ConfidenceHigh
        confidence >= 0.6 -> com.example.feedsense.ui.theme.ConfidenceMedium
        else -> com.example.feedsense.ui.theme.ConfidenceLow
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = displayText,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// --------------------------------
// CATEGORY CHIP
// --------------------------------
//
// Milestone 8A/8K. Colored category label.
//

@Composable
fun CategoryChip(
    category: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val color = categoryColor(category)

    val bgColor = if (selected) {
        color.copy(alpha = 0.25f)
    } else {
        color.copy(alpha = 0.12f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = category.replace("_", " ")
                .replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// --------------------------------
// PLATFORM CHIP
// --------------------------------

@Composable
fun PlatformChip(
    platform: String,
    modifier: Modifier = Modifier
) {
    val color = platformColor(platform)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = platform,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// --------------------------------
// EMPTY STATE
// --------------------------------
//
// Milestone 8U. Consistent empty-state display.
//

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.5f
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.7f
            )
        )
    }
}

// --------------------------------
// AI STATUS INDICATOR
// --------------------------------
//
// Milestone 8Q. Shows whether local AI is active.
//

@Composable
fun AIStatusIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    modelVersion: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) {
                        com.example.feedsense.ui.theme.StatusActive
                    } else {
                        com.example.feedsense.ui.theme.StatusInactive
                    }
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = if (isActive) "LOCAL AI ACTIVE" else "LOCAL AI INACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) {
                    com.example.feedsense.ui.theme.StatusActive
                } else {
                    com.example.feedsense.ui.theme.StatusInactive
                },
                fontWeight = FontWeight.SemiBold
            )

            if (modelVersion != null) {
                Text(
                    text = "Model: $modelVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --------------------------------
// INTERACTION TAG
// --------------------------------
//
// Milestone 8I. Small tag for interaction signals.
//

@Composable
fun InteractionTag(
    signal: String,
    modifier: Modifier = Modifier
) {
    val displayText = when (signal) {
        "skipped" -> "Skipped"
        "liked" -> "Liked"
        "commented" -> "Commented"
        "shared" -> "Shared"
        "saved" -> "Saved"
        "followed" -> "Followed"
        "paused" -> "Paused"
        "playing" -> "Playing"
        "reference_refined" -> null
        "reference_supported" -> null
        "reference_boosted" -> null
        "human_validated" -> null
        else -> null
    }

    if (displayText != null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = displayText,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 3.dp
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --------------------------------
// COLOR MAPPINGS
// --------------------------------

@Composable
fun categoryColor(category: String): Color {
    return when (category.lowercase()) {
        "sports" -> com.example.feedsense.ui.theme.ChipSports
        "comedy", "meme", "memes" -> com.example.feedsense.ui.theme.ChipComedy
        "motivation", "self_improvement" -> com.example.feedsense.ui.theme.ChipMotivation
        "music", "music_video" -> com.example.feedsense.ui.theme.ChipMusic
        "educational", "tutorial", "how_to" -> com.example.feedsense.ui.theme.ChipEducation
        "news", "politics" -> com.example.feedsense.ui.theme.ChipNews
        "gaming", "gameplay", "esports" -> com.example.feedsense.ui.theme.ChipGaming
        "technology", "tech" -> com.example.feedsense.ui.theme.ChipTechnology
        "food", "cooking" -> com.example.feedsense.ui.theme.ChipFood
        "travel" -> com.example.feedsense.ui.theme.ChipTravel
        "lifestyle", "fashion", "beauty" -> com.example.feedsense.ui.theme.ChipLifestyle
        else -> com.example.feedsense.ui.theme.ChipDefault
    }
}

@Composable
fun platformColor(platform: String): Color {
    return when (platform.lowercase()) {
        "instagram" -> com.example.feedsense.ui.theme.PlatformInstagram
        "youtube" -> com.example.feedsense.ui.theme.PlatformYouTube
        "tiktok" -> com.example.feedsense.ui.theme.PlatformTikTok
        "facebook" -> com.example.feedsense.ui.theme.PlatformFacebook
        "twitter" -> com.example.feedsense.ui.theme.PlatformTwitter
        "reddit" -> com.example.feedsense.ui.theme.PlatformReddit
        "linkedin" -> com.example.feedsense.ui.theme.PlatformLinkedIn
        else -> com.example.feedsense.ui.theme.PlatformDefault
    }
}
