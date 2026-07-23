package com.appblish.jgallery.feature.onboarding.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.appblish.jgallery.feature.onboarding.TrustCopy

/**
 * Onboarding step 3 (spec §9.3, artboard a03): the branded trust overlay shown while the user is on
 * the system All-Files page. It points the user at the toggle and — ONLY once Security has signed off
 * ([TrustCopy.claimApproved]) — displays the green-shield "Safe & Secure" claim.
 *
 * Integrity gate (contract §5, spec §9.3, HARD): until the Security Engineer sign-off flips
 * [TrustCopy.signOff] to Approved, this renders the claim-free variant — a plain accent pointer with
 * no safety promise. That makes it impossible to ship the unverified claim before sign-off, while the
 * rest of the onboarding flow stays fully wired. All copy comes from the single [TrustCopy] home.
 */
@Composable
fun TrustOverlayScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Background is edge-to-edge; content sits in the safe area so the heading clears the status
        // bar and the down-pointer/caption clear the nav bar (design §Inset, item 10).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))

            if (TrustCopy.claimApproved) {
                TrustClaim()
            } else {
                ClaimFreePointer()
            }

            Spacer(Modifier.weight(1f))

            // Points down at the real system toggle at the bottom of the All-Files page.
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = TrustCopy.POINTER,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Branded safety claim — renders ONLY after Security sign-off. */
@Composable
private fun TrustClaim() {
    Box(
        modifier = Modifier
            .size(88.dp)
            .background(Color(0x1423A55A), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = JGalleryColors.TrustGreen,
            modifier = Modifier.size(48.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = TrustCopy.TITLE,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 22.sp,
        fontWeight = FontWeight.W800,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = TrustCopy.BODY,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
    )
}

/**
 * Pre-sign-off variant: a neutral, claim-free heading. NO shield, NO "Safe & Secure" — nothing that
 * asserts a security property, because that assertion is what Security must clear first (contract §5).
 */
@Composable
private fun ClaimFreePointer() {
    Text(
        text = "Enable access for JGallery",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 22.sp,
        fontWeight = FontWeight.W800,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
