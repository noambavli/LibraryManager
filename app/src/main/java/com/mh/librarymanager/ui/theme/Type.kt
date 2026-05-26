package com.mh.librarymanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val HebrewFamily = FontFamily.Default

private fun base(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    letter: Double = 0.0,
) = TextStyle(
    fontFamily = HebrewFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letter.sp,
)

val Typography = Typography(
    displayLarge = base(48, 56, FontWeight.SemiBold),
    displayMedium = base(40, 48, FontWeight.SemiBold),
    displaySmall = base(32, 40, FontWeight.SemiBold),
    headlineLarge = base(28, 36, FontWeight.SemiBold),
    headlineMedium = base(24, 32, FontWeight.SemiBold),
    headlineSmall = base(20, 28, FontWeight.SemiBold),
    titleLarge = base(20, 28, FontWeight.SemiBold),
    titleMedium = base(18, 24, FontWeight.Medium),
    titleSmall = base(15, 20, FontWeight.Medium),
    bodyLarge = base(17, 24),
    bodyMedium = base(15, 22),
    bodySmall = base(13, 18),
    labelLarge = base(15, 20, FontWeight.Medium),
    labelMedium = base(13, 18, FontWeight.Medium),
    labelSmall = base(11, 16, FontWeight.Medium),
)
