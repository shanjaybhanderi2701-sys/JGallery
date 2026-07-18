package com.appblish.jgallery.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A single bundled open-source dependency + its license (design SET-04, static list). */
data class OssLicense(val name: String, val license: String)

/**
 * Static list of the key OSS dependencies Filora ships (design SET-04). A simple curated list is
 * sufficient for launch; if the set grows, swap in a generated licenses report. Not exhaustive of
 * transitive deps — the headline libraries the app is built on.
 */
val OSS_LICENSES: List<OssLicense> = listOf(
    OssLicense("AndroidX (Core, Lifecycle, Activity, Navigation)", "Apache License 2.0"),
    OssLicense("Jetpack Compose", "Apache License 2.0"),
    OssLicense("Material Components / Material 3", "Apache License 2.0"),
    OssLicense("AndroidX DataStore", "Apache License 2.0"),
    OssLicense("Dagger Hilt", "Apache License 2.0"),
    OssLicense("Kotlin & Kotlin Coroutines", "Apache License 2.0"),
    OssLicense("Media3 (ExoPlayer)", "Apache License 2.0"),
    OssLicense("Coil", "Apache License 2.0"),
)

/** Open-source licenses list (design SET-04). Full-screen back-arrow chrome. */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("licenses_screen"),
    ) {
        SettingsHeader(title = "Open-source licenses", onBack = onBack)
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(OSS_LICENSES) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = entry.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entry.license,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
