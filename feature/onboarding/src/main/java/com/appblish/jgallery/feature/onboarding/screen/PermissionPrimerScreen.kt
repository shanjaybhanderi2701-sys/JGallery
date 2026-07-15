package com.appblish.jgallery.feature.onboarding.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens
import com.appblish.jgallery.feature.onboarding.OnboardingCopy

/**
 * Onboarding step 2 (spec §9.2, artboard a02): the permission primer, an iOS-style bottom sheet over a
 * scrim. Copy is spec-locked ([OnboardingCopy.PRIMER_TITLE] / `PRIMER_BODY` / single `PRIMER_CTA`).
 * The mock system toggle previews what the user will flip on the real All-Files page; it is display-only.
 */
@Composable
fun PermissionPrimerScreen(
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x66000000)), // scrim
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = JGalleryColors.Background,
            shape = JGalleryDimens.SheetRadius,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                // Sheet colour fills to the bottom edge behind the nav bar; the content (incl. the
                // Allow CTA) is padded above it so it never clips (design §Inset, item 10).
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Grab handle.
                Box(
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .size(width = JGalleryDimens.GrabHandleWidth, height = JGalleryDimens.GrabHandleHeight)
                        .background(JGalleryColors.Surface, RoundedCornerShape(3.dp)),
                )

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(JGalleryColors.AccentSoft, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = JGalleryColors.Accent,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = OnboardingCopy.PRIMER_TITLE,
                    color = JGalleryColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W800,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = OnboardingCopy.PRIMER_BODY,
                    color = JGalleryColors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))
                MockSystemToggle()

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth().height(JGalleryDimens.ButtonHeight),
                    shape = JGalleryDimens.ButtonRadius,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JGalleryColors.Accent,
                        contentColor = JGalleryColors.OnAccent,
                    ),
                ) {
                    Text(OnboardingCopy.PRIMER_CTA, fontSize = 16.sp, fontWeight = FontWeight.W600)
                }
            }
        }
    }
}

/** Display-only preview of the system toggle the user will enable (spec §9.2). Not interactive. */
@Composable
private fun MockSystemToggle() {
    Surface(
        color = JGalleryColors.Surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = OnboardingCopy.PRIMER_TOGGLE_LABEL,
                color = JGalleryColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = false,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = JGalleryColors.Accent,
                    uncheckedTrackColor = Color(0xFFCBD0D8),
                ),
            )
        }
    }
}
