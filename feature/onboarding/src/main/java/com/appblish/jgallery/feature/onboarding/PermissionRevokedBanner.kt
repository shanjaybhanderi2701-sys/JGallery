package com.appblish.jgallery.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Dim + blur the last-known grid behind the revoked banner (design W3-07): the content stays visible
 * so the screen isn't hijacked, but it reads as frozen/unreliable. Blur no-ops below API 31; the
 * alpha dim still lands, so the effect degrades gracefully. A no-op when [active] is false.
 */
fun Modifier.revokedGridScrim(active: Boolean): Modifier =
    if (active) this.alpha(0.35f).blur(8.dp) else this

/**
 * In-context "storage access was turned off" banner (design W3-07). A soft-red card with a lock-off
 * glyph that sits over the last-known grid — it does NOT hijack the screen; the host keeps the grid
 * visible but dimmed/blurred behind it (see [Modifier.revokedGridScrim]) because it can no longer be
 * reliably re-read. The single action opens the [ReRequestSheet] recovery path.
 *
 * Copy is centralised in [PermissionRecoveryCopy]; when [permanentlyDenied] the extra hint tells the
 * user the toggle now lives in system settings. Drive visibility from `StorageAccessMonitor.state`
 * via [permissionRecoveryUi] — never from a permission name (spec §1.6/§9.4).
 */
@Composable
fun PermissionRevokedBanner(
    onReRequest: () -> Unit,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(JGalleryColors.CorruptFill, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("permission_revoked_banner"),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = JGalleryColors.Danger,
            modifier = Modifier.size(28.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.padding(start = 14.dp).fillMaxWidth()) {
            Text(
                text = PermissionRecoveryCopy.BANNER_TITLE,
                color = JGalleryColors.Text,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = PermissionRecoveryCopy.BANNER_BODY,
                color = JGalleryColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (permanentlyDenied) {
                Text(
                    text = PermissionRecoveryCopy.PERMANENTLY_DENIED_HINT,
                    color = JGalleryColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(
                onClick = onReRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JGalleryColors.Accent,
                    contentColor = JGalleryColors.OnAccent,
                ),
                modifier = Modifier.padding(top = 12.dp).testTag("permission_revoked_action"),
            ) {
                Text(PermissionRecoveryCopy.BANNER_ACTION, textAlign = TextAlign.Center)
            }
        }
    }
}
