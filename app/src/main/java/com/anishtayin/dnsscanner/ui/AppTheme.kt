package com.anishtayin.dnsscanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anishtayin.dnsscanner.dns.ResolverStatus

/**
 * تم اختصاصی برنامه: آبیِ برند + فیروزه‌ای، با پشتیبانی کامل از حالت تیره
 * و تایپوگرافی بزرگ‌تر برای خوانایی فارسی.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF0B3D91),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFFF9A825),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE3EAF2),
    onSurfaceVariant = Color(0xFF44474E),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF601410),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFC107),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE3E4E8),
    surfaceVariant = Color(0xFF1E2A38),
    onSurfaceVariant = Color(0xFFB8C2CC),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
)

@Composable
fun DnsScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** رنگ‌های نشان وضعیت: (پس‌زمینه‌ی نشان، رنگ متن روی آن). */
data class StatusColors(val container: Color, val onContainer: Color)

@Composable
fun statusColors(status: ResolverStatus): StatusColors {
    val dark = isSystemInDarkTheme()
    return when (status) {
        ResolverStatus.WORKING -> if (dark) {
            StatusColors(Color(0xFF1E4620), Color(0xFFA5D6A7))
        } else {
            StatusColors(Color(0xFFDFF2E3), Color(0xFF1B5E20))
        }
        ResolverStatus.SLOW -> if (dark) {
            StatusColors(Color(0xFF4A3600), Color(0xFFFFD54F))
        } else {
            StatusColors(Color(0xFFFFF3D6), Color(0xFF7A5C00))
        }
        ResolverStatus.NO_ANSWER, ResolverStatus.WRONG_ANSWER -> if (dark) {
            StatusColors(Color(0xFF5C1A15), Color(0xFFFFB4AB))
        } else {
            StatusColors(Color(0xFFFDECEA), Color(0xFFB3261E))
        }
    }
}

/** رنگ میله‌ی تأخیر بر اساس وضعیت سرور. */
@Composable
fun latencyBarColor(status: ResolverStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        ResolverStatus.WORKING -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        ResolverStatus.SLOW -> if (dark) Color(0xFFFFCA28) else Color(0xFFF9A825)
        else -> MaterialTheme.colorScheme.error
    }
}
