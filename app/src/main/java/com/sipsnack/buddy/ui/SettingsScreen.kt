package com.sipsnack.buddy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.rules.Moment
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onSharing: () -> Unit) {
    val goal by vm.goal.collectAsState()
    val memesOn by vm.memesOn.collectAsState()
    val nudgesOn by vm.nudgesOn.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    val stepGoal by vm.stepGoal.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Sip.Ink).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                @Suppress("DEPRECATION")
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Sip.Cloud)
            }
            Text("Settings", style = SipType.Title)
        }

        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daily water goal", style = SipType.Section)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton("−", onClick = { vm.setGoal(goal - 1) })
                Text(com.sipsnack.buddy.rules.Units.ml(goal.toDouble()), style = SipType.Title)
                OutlineButton("+", onClick = { vm.setGoal(goal + 1) })
            }
            Text("Changes in steps of 250 ml. Check with your doctor before changing it.", style = SipType.Small)
        }

        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daily step goal", style = SipType.Section)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton("−", onClick = { vm.setStepGoal(stepGoal - 500) })
                Text("%,d".format(java.util.Locale.US, stepGoal), style = SipType.Title)
                OutlineButton("+", onClick = { vm.setStepGoal(stepGoal + 500) })
            }
            Text("Start gentle and build up. Your doctor can say what's right for you.", style = SipType.Small)
        }

        Column(Modifier.fillMaxWidth().card().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Toggle("Meme videos and sounds", "Celebrations when you log and hit your goal", memesOn, vm::setMemesOn)
            Toggle("Delay and meal nudges", "The side-eye, Akrobeto and meal reminders. 8 am to 10 pm, at most 3 a day.", nudgesOn, vm::setNudgesOn)
        }

        if (com.sipsnack.buddy.sync.Pairing.available && vm.role.value == "her") {
            Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sharing with ${com.sipsnack.buddy.BUDDY_NAME}", style = SipType.Section)
                Text("Choose what he sees, pause, or stop.", style = SipType.Small)
                OutlineButton("Open sharing", onClick = onSharing)
            }
        }

        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preview the memes", style = SipType.Section)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Halfway", onClick = { vm.moment.value = Moment.HALFWAY })
                OutlineButton("Goal hit", onClick = { vm.moment.value = Moment.GOAL_HIT })
                OutlineButton("Delay", onClick = { vm.moment.value = Moment.DELAY })
                OutlineButton("Still delaying", onClick = { vm.moment.value = Moment.STILL_DELAYING })
            }
        }

        TextButton(onClick = { confirmWipe = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Delete all my data", style = SipType.Body.copy(color = Sip.Sky))
        }
        TextButton(onClick = vm::switchRole, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Wrong phone role? Switch phone role", style = SipType.Small.copy(color = Sip.Muted))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?", style = SipType.Section) },
            text = { Text("All your logs go, for good. Settings stay.", style = SipType.Body) },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmWipe = false }) { Text("Delete", color = Sip.Sky) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Keep them", color = Sip.Muted) } },
            containerColor = Sip.Deep,
        )
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = SipType.BodyStrong)
            Text(detail, style = SipType.Small)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Sip.Electric, checkedThumbColor = Sip.Ink, uncheckedTrackColor = Sip.Deep, uncheckedThumbColor = Sip.Muted, uncheckedBorderColor = Sip.Line),
        )
    }
}
