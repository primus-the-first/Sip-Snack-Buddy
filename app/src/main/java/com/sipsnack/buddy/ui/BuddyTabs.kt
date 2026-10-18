package com.sipsnack.buddy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType

/** His phone: her day and his own day, side by side as two tabs. */
@Composable
fun BuddyTabs(current: String, onPick: (String) -> Unit, content: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize().background(Sip.Ink)) {
        content(Modifier.weight(1f))
        HorizontalDivider(color = Sip.Deep)
        Row(Modifier.fillMaxWidth().background(Sip.Night).navigationBarsPadding().height(64.dp)) {
            Tab("Her day", current == "her", Modifier.weight(1f)) { onPick("her") }
            Tab("My day", current == "today", Modifier.weight(1f)) { onPick("today") }
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().semantics { this.selected = selected }.clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(8.dp).background(if (selected) Sip.Electric else Sip.Night, CircleShape))
        Text(label, style = SipType.Label.copy(color = if (selected) Sip.Cloud else Sip.Muted))
    }
}
