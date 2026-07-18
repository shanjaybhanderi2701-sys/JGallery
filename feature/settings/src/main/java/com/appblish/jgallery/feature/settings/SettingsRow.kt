package com.appblish.jgallery.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The trailing accessory of a [SettingsRow] (design §7). One component, three variants:
 * a muted value string, an accent-soft pill for live state (e.g. the current theme), or a chevron
 * for rows that open a route/dialog with no inline value.
 */
sealed interface SettingsTrailing {
    /** Muted right-aligned value text (e.g. "3 columns"), optionally followed by a chevron. */
    data class Value(val text: String, val chevron: Boolean = true) : SettingsTrailing
    /** Accent-soft pill carrying live state (e.g. the selected theme). */
    data class Pill(val text: String) : SettingsTrailing
    /** Chevron only — the row opens something with no inline value. */
    data object Chevron : SettingsTrailing
    /** Nothing (read-only rows such as Version). */
    data object None : SettingsTrailing
}

/**
 * One Settings row (design §7 anatomy): 58dp min height, 26dp leading icon, 16dp gutters, a 17sp/600
 * title over an optional 13.5sp subtitle, and a trailing value / pill / chevron. The whole row is a
 * single ≥48dp tap target — the label is the accessible name (icons are decorative).
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    trailing: SettingsTrailing,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.5.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Trailing(trailing)
    }
}

@Composable
private fun Trailing(trailing: SettingsTrailing) {
    val colors = MaterialTheme.colorScheme
    when (trailing) {
        is SettingsTrailing.Value -> {
            Text(
                text = trailing.text,
                fontSize = 15.sp,
                color = colors.onSurfaceVariant,
            )
            if (trailing.chevron) Chevron()
        }
        is SettingsTrailing.Pill -> {
            Text(
                text = trailing.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier
                    .background(colors.primaryContainer, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        SettingsTrailing.Chevron -> Chevron()
        SettingsTrailing.None -> Unit
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(20.dp),
    )
}
