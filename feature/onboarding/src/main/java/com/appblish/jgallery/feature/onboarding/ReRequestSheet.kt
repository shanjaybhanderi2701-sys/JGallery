package com.appblish.jgallery.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.component.JGallerySheet
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Mid-session recovery sheet (design W3-07). Reuses the onboarding trust language verbatim — the honest
 * reason, the mock system toggle, and the SINGLE audited safety claim [TrustCopy.BODY] (only rendered
 * once [TrustCopy.claimApproved], never re-worded) — so it makes **no new claims** beyond W1-02/03
 * (spec §9.2/§9.3). "Open settings" hands back to the host, which acts on the boundary's
 * `AccessRequest.SystemSettings` to deep-link the system All-Files page; the sheet never names a
 * permission or builds an intent itself.
 *
 * @param permanentlyDenied when true the system won't re-prompt, so the sheet adds the settings-only
 *   hint; "Open settings" is the primary path and "Not now" stays as the soft escape.
 * @param onOpenSettings host launches the boundary's settings request; on return it calls
 *   `StorageAccessMonitor.refresh()`, which clears the banner if access came back.
 */
@Composable
fun ReRequestSheet(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
) {
    JGallerySheet(
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        modifier = modifier.testTag("re_request_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = PermissionRecoveryCopy.SHEET_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = JGalleryColors.Text,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = PermissionRecoveryCopy.SHEET_REASON,
                color = JGalleryColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Mock of the system toggle the user will flip on the All-Files page (display-only, matches
            // the onboarding primer). Not interactive: the real toggle lives in system settings.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .background(JGalleryColors.Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = OnboardingCopy.PRIMER_TOGGLE_LABEL,
                    color = JGalleryColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = false,
                    onCheckedChange = null, // display-only
                    colors = SwitchDefaults.colors(),
                    modifier = Modifier.testTag("re_request_toggle_mock"),
                )
            }

            // The single audited safety claim — reused, never a new claim (spec §9.3). Rendered only
            // once Security has signed off ([TrustCopy.claimApproved]); otherwise the sheet stays claim-free.
            if (TrustCopy.claimApproved) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = JGalleryColors.TrustGreen,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = TrustCopy.BODY,
                        color = JGalleryColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (permanentlyDenied) {
                Text(
                    text = PermissionRecoveryCopy.PERMANENTLY_DENIED_HINT,
                    color = JGalleryColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JGalleryColors.Accent,
                    contentColor = JGalleryColors.OnAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .testTag("re_request_open_settings"),
            ) {
                Text(PermissionRecoveryCopy.SHEET_OPEN_SETTINGS)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 4.dp).testTag("re_request_not_now"),
            ) {
                Text(PermissionRecoveryCopy.SHEET_NOT_NOW, color = JGalleryColors.TextSecondary)
            }
        }
    }
}
