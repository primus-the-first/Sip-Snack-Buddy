package com.sipsnack.buddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.ui.theme.SipType

/** Shows only when GitHub has a newer release. One tap downloads it; Android then asks to install. */
@Composable
fun UpdateCard(vm: AppViewModel) {
    val release by vm.update.collectAsState()
    val updating by vm.updating.collectAsState()
    val r = release ?: return
    Row(
        Modifier.fillMaxWidth().card().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("New version ready", style = SipType.BodyStrong)
            Text(if (updating) "Downloading ${r.version}..." else "Version ${r.version}. Your logs stay.", style = SipType.Small)
        }
        if (!updating) OutlineButton("Update", onClick = vm::installUpdate)
    }
}
