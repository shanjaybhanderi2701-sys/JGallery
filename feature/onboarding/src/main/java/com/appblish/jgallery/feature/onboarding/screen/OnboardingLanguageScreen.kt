package com.appblish.jgallery.feature.onboarding.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens
import com.appblish.jgallery.feature.onboarding.OnboardingCopy
import com.appblish.jgallery.feature.onboarding.OnboardingLanguage

/**
 * Onboarding step 1 (spec §9.1, artboard a01): pick the app language. Selection is local until the
 * user taps [OnboardingCopy.LANGUAGE_CTA]; only then does the ViewModel persist it.
 */
@Composable
fun OnboardingLanguageScreen(
    selected: OnboardingLanguage,
    onSelect: (OnboardingLanguage) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = JGalleryColors.Background) {
        // Background fills edge-to-edge; the content stays inside the safe area so the title never
        // clips under the status bar and the CTA never clips under the nav bar (design §Inset, item 10).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = OnboardingCopy.LANGUAGE_TITLE,
                color = JGalleryColors.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.W800,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = OnboardingCopy.LANGUAGE_SUBTITLE,
                color = JGalleryColors.TextSecondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(OnboardingLanguage.entries) { language ->
                    LanguageRow(
                        language = language,
                        selected = language == selected,
                        onClick = { onSelect(language) },
                    )
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(JGalleryDimens.ButtonHeight),
                shape = JGalleryDimens.ButtonRadius,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JGalleryColors.Accent,
                    contentColor = JGalleryColors.OnAccent,
                ),
            ) {
                Text(OnboardingCopy.LANGUAGE_CTA, fontSize = 16.sp, fontWeight = FontWeight.W600)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageRow(
    language: OnboardingLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) JGalleryColors.AccentSoft else JGalleryColors.Background,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = JGalleryColors.Accent),
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = language.displayName,
                    color = JGalleryColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                )
                Text(
                    text = language.endonym,
                    color = JGalleryColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
