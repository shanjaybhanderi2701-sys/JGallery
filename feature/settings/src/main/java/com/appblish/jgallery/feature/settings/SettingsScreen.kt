package com.appblish.jgallery.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.ThemeMode
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.SortBySheet

/** Stateful entry — wires the Hilt VM to the stateless body (instrumented tests drive the body). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onThemeChange = viewModel::setThemeMode,
        onSortChange = viewModel::setDefaultSort,
        onColumnsChange = viewModel::setDefaultColumns,
        onSlideshowChange = viewModel::setSlideshowIntervalMs,
        onOpenAbout = onOpenAbout,
        modifier = modifier,
    )
}

/**
 * Settings root (design G2 Settings, SET-01). Full-screen route: a back-arrow header over grouped
 * `Card` sections — Appearance / Gallery defaults / Playback / About — plus a muted version footer.
 * Every control applies instantly (no confirm): Theme opens a radio dialog, Default sort and Grid
 * density reuse the shared [SortBySheet] / [ColumnCountSheet] verbatim, Slideshow opens a small
 * interval dialog. State is static & local (§2) — no loading/error/empty states.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onThemeChange: (ThemeMode) -> Unit = {},
    onSortChange: (SortSpec) -> Unit = {},
    onColumnsChange: (ColumnCount) -> Unit = {},
    onSlideshowChange: (Long) -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showColumnSheet by remember { mutableStateOf(false) }
    var showSlideshowDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("settings_screen"),
    ) {
        SettingsHeader(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(title = "Appearance") {
                SettingsRow(
                    icon = state.themeMode.icon,
                    title = "Theme",
                    trailing = SettingsTrailing.Pill(state.themeMode.label),
                    onClick = { showThemeDialog = true },
                    modifier = Modifier.testTag("settings_row_theme"),
                )
            }

            SettingsSection(title = "Gallery defaults") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    title = "Default sort",
                    trailing = SettingsTrailing.Value(state.defaultSort.label),
                    onClick = { showSortSheet = true },
                    modifier = Modifier.testTag("settings_row_sort"),
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Outlined.GridView,
                    title = "Grid density",
                    trailing = SettingsTrailing.Value("${state.defaultColumns.value} columns"),
                    onClick = { showColumnSheet = true },
                    modifier = Modifier.testTag("settings_row_columns"),
                )
            }

            SettingsSection(title = "Playback") {
                SettingsRow(
                    icon = Icons.Outlined.Slideshow,
                    title = "Slideshow interval",
                    trailing = SettingsTrailing.Value(slideshowLabel(state.slideshowIntervalMs)),
                    onClick = { showSlideshowDialog = true },
                    modifier = Modifier.testTag("settings_row_slideshow"),
                )
            }

            SettingsSection(title = "About") {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "About Filora",
                    subtitle = "Version, licenses & privacy",
                    trailing = SettingsTrailing.Chevron,
                    onClick = onOpenAbout,
                    modifier = Modifier.testTag("settings_row_about"),
                )
            }

            Text(
                text = "Filora · ${AppVersion.NAME} (${AppVersion.CODE})",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            current = state.themeMode,
            onSelect = { onThemeChange(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showSortSheet) {
        // Reused verbatim (§3). Writing here updates the app-wide default sort seed.
        SortBySheet(
            current = state.defaultSort,
            onSelect = onSortChange,
            onDismiss = { showSortSheet = false },
        )
    }
    if (showColumnSheet) {
        // Reused verbatim (§3). Sets the starting grid density; pinch/per-tab still overrides.
        ColumnCountSheet(
            current = state.defaultColumns,
            onSelect = { onColumnsChange(it); showColumnSheet = false },
            onDismiss = { showColumnSheet = false },
        )
    }
    if (showSlideshowDialog) {
        SlideshowIntervalDialog(
            currentMs = state.slideshowIntervalMs,
            onSelect = { onSlideshowChange(it); showSlideshowDialog = false },
            onDismiss = { showSlideshowDialog = false },
        )
    }
}

/** Full-screen back-arrow header (matches the trash/viewer full-screen chrome pattern). */
@Composable
internal fun SettingsHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Accent caps section label (§7: 13sp/700) over a rounded surface card grouping its rows. */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)),
        ) { content() }
    }
}

@Composable
private fun SectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 58.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

/** Theme radio dialog (SET-02): System / Light / Dark, applies instantly on select. */
@Composable
private fun ThemePickerDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Theme") },
        text = {
            Column(modifier = Modifier.testTag("theme_picker_dialog")) {
                ThemeMode.entries.forEach { mode ->
                    RadioOption(
                        icon = mode.icon,
                        label = mode.label,
                        selected = mode == current,
                        onClick = { onSelect(mode) },
                        testTag = "theme_option_${mode.name.lowercase()}",
                    )
                }
            }
        },
    )
}

@Composable
private fun RadioOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Slideshow interval dialog (§6): 2s / 4s / 6s / 10s, applies instantly on select. */
@Composable
private fun SlideshowIntervalDialog(
    currentMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Slideshow interval") },
        text = {
            Column(modifier = Modifier.testTag("slideshow_interval_dialog")) {
                SLIDESHOW_INTERVALS_MS.forEach { ms ->
                    RadioOption(
                        icon = Icons.Outlined.Slideshow,
                        label = slideshowLabel(ms),
                        selected = ms == currentMs,
                        onClick = { onSelect(ms) },
                        testTag = "slideshow_option_${ms / 1000}s",
                    )
                }
            }
        },
    )
}

private val SLIDESHOW_INTERVALS_MS = listOf(2_000L, 4_000L, 6_000L, 10_000L)

private fun slideshowLabel(ms: Long): String = "${ms / 1000}s"

/** App version, surfaced from the settings-module BuildConfig (design SET-04). */
private object AppVersion {
    val NAME: String = BuildConfig.VERSION_NAME
    val CODE: Int = BuildConfig.VERSION_CODE
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System default"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
    }

/** "Newest first" / "A–Z" style summary of the app-wide default sort (§3). */
private val SortSpec.label: String
    get() {
        val asc = direction == SortDirection.ASCENDING
        return when (key) {
            SortKey.LAST_MODIFIED -> if (asc) "Oldest first" else "Newest first"
            SortKey.FILE_NAME -> if (asc) "Name A–Z" else "Name Z–A"
            SortKey.FILE_SIZE -> if (asc) "Smallest first" else "Largest first"
            SortKey.FILE_PATH -> if (asc) "Path A–Z" else "Path Z–A"
        }
    }
