package com.sipsnack.buddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sipsnack.buddy.R

/** Midnight Blue: her colours. Matches the design canvas. */
object Sip {
    val Ink = Color(0xFF07090F)
    val Night = Color(0xFF10141F)
    val Deep = Color(0xFF182033)
    val Line = Color(0xFF263048)
    val Electric = Color(0xFF3D8BFF)
    val Sky = Color(0xFF6FA8FF)
    val Ice = Color(0xFFBFD8FF)
    val Cloud = Color(0xFFEEF3FF)
    val Muted = Color(0xFF9AA6C2)
}

val Sora = FontFamily(
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

val DmSans = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_bold, FontWeight.Bold),
)

object SipType {
    val Hero = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Sip.Cloud)
    val Title = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Sip.Cloud)
    val Section = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Sip.Cloud)
    val Body = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, color = Sip.Cloud)
    val BodyStrong = Body.copy(fontWeight = FontWeight.Bold)
    val Small = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = Sip.Muted)
    val Label = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Sip.Cloud)
}

@Composable
fun SipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sip.Electric,
            onPrimary = Sip.Ink,
            background = Sip.Ink,
            onBackground = Sip.Cloud,
            surface = Sip.Night,
            onSurface = Sip.Cloud,
            surfaceVariant = Sip.Deep,
            outline = Sip.Line,
        ),
        content = content,
    )
}
