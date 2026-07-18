package com.appblish.jgallery.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Placeholder no-data-collected privacy policy URL. */
// TODO(PM): APP-514 policy URL — replace with the published no-data-collected policy link.
const val PRIVACY_POLICY_URL: String = "https://filora.app/privacy"

/**
 * About screen (design SET-04): app version (from BuildConfig), an open-source licenses route, and a
 * privacy-policy row that opens the policy in a browser (ACTION_VIEW) with a graceful no-op if no
 * browser is installed. Full-screen back-arrow chrome shared with the Settings root.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .testTag("about_screen"),
    ) {
        SettingsHeader(title = "About", onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(title = "Filora") {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    trailing = SettingsTrailing.Value(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        chevron = false,
                    ),
                    modifier = Modifier.testTag("about_version"),
                )
            }
            SettingsSection(title = "Legal") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = "Open-source licenses",
                    trailing = SettingsTrailing.Chevron,
                    onClick = onOpenLicenses,
                    modifier = Modifier.testTag("about_licenses"),
                )
                SettingsRow(
                    icon = Icons.Outlined.Policy,
                    title = "Privacy policy",
                    trailing = SettingsTrailing.Chevron,
                    onClick = {
                        // Graceful no-op if no browser can handle the intent (design SET-04).
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            // No browser available — silently ignore rather than crash.
                        }
                    },
                    modifier = Modifier.testTag("about_privacy"),
                )
            }
        }
    }
}
