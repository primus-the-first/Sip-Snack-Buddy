package com.sipsnack.buddy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType

private val Pill = RoundedCornerShape(999.dp)

/** The one electric-blue button on a screen. Black text on blue reads clearest. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Sip.Electric,
            contentColor = Sip.Ink,
            disabledContainerColor = Sip.Deep,
            disabledContentColor = Sip.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text, style = SipType.BodyStrong.copy(color = if (enabled) Sip.Ink else Sip.Muted)) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = Pill,
        border = BorderStroke(1.5.dp, Sip.Electric),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { Text(text, style = SipType.Body) }
}

enum class PillKind { Soft, Strong, Behind }

/** Status pill. "Behind" is a dashed outline plus words, never colour alone. */
@Composable
fun StatusPill(text: String, kind: PillKind) {
    val base = Modifier.heightIn(min = 32.dp)
    val styled = when (kind) {
        PillKind.Soft -> base.background(Sip.Deep, Pill)
        PillKind.Strong -> base.background(Sip.Electric, Pill)
        PillKind.Behind -> base.drawBehind {
            drawRoundRect(
                color = Sip.Sky,
                cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
        }
    }
    Box(styled.padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = SipType.Label.copy(color = if (kind == PillKind.Strong) Sip.Ink else Sip.Ice))
    }
}

fun Modifier.card() = this
    .background(Sip.Night, RoundedCornerShape(28.dp))
    .border(1.dp, Sip.Line, RoundedCornerShape(28.dp))
