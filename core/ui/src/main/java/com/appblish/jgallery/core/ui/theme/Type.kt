package com.appblish.jgallery.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Type scale from the Wave 1 spec (§1). Big, bold tab titles; heavy date headers. */
val JGalleryTypography = Typography(
    // Tab titles: Albums / Photos / Collections / Search — 32sp / 800.
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
    // Date section headers: Today / Yesterday / DD/MM/YYYY — 22sp / 800.
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
    // Album name 15sp / 600.
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    // Counts / captions 13sp.
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)
