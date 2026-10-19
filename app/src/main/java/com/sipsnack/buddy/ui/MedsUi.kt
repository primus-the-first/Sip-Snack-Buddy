package com.sipsnack.buddy.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.BUDDY_NAME
import com.sipsnack.buddy.rules.Med
import com.sipsnack.buddy.rules.SharedMed
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("h:mm a")

fun minuteLabel(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(clock)

private fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(clock)

/** Today's medicines with one tap to tick each off. Hidden when she has none. */
@Composable
fun MedsCard(vm: AppViewModel) {
    val meds by vm.meds.collectAsState()
    vm.medLogs.collectAsState().value // re-read check-offs as they change
    if (meds.isEmpty()) return
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Medication", style = SipType.Section)
        meds.forEachIndexed { i, med ->
            if (i > 0) HorizontalDivider(color = Sip.Deep)
            val taken = vm.takenToday(med)
            val streak = vm.streak(med)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(med.name, style = SipType.BodyStrong)
                    Text(
                        when {
                            taken != null && streak >= 2 -> "Taken ${timeLabel(taken)} · $streak days in a row"
                            taken != null -> "Taken ${timeLabel(taken)}"
                            streak >= 2 -> "Due ${minuteLabel(med.minuteOfDay)} · $streak days in a row"
                            else -> "Due ${minuteLabel(med.minuteOfDay)}"
                        },
                        style = SipType.Small,
                    )
                }
                if (taken != null) StatusPill("Taken", PillKind.Strong) else OutlineButton("Taken", onClick = { vm.takeMed(med) })
            }
        }
    }
}

/** Settings: her medicines, each with its time. Add, change the time, or remove. */
@Composable
fun MedsSettings(vm: AppViewModel) {
    val context = LocalContext.current
    val meds by vm.meds.collectAsState()
    var adding by remember { mutableStateOf(false) }
    fun pickTime(start: Int, onPick: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, start / 60, start % 60, false).show()
    }

    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Medications", style = SipType.Section)
        Text("Once a day each, at your time. The app only reminds you; it never suggests doses.", style = SipType.Small)
        meds.forEach { med ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(med.name, style = SipType.BodyStrong, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickTime(med.minuteOfDay) { vm.setMedTime(med.id, it) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(minuteLabel(med.minuteOfDay), color = Sip.Sky)
                }
                TextButton(onClick = { vm.removeMed(med.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Remove", color = Sip.Muted)
                }
            }
        }
        OutlineButton("Add medication", onClick = { adding = true })
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add medication", style = SipType.Section) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sip.Electric, focusedLabelColor = Sip.Sky),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val chosen = name
                    adding = false
                    pickTime(8 * 60) { vm.addMed(chosen, it) }
                }) { Text("Choose time", color = Sip.Sky) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = Sip.Muted) } },
            containerColor = Sip.Deep,
        )
    }
}

/** The one-time question on her phone: medicine is personal, so sharing it is a separate yes. */
@Composable
fun ShareMedsCard(onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Sip.Ink.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding().padding(20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.fillMaxWidth().card().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Share your medicine check-offs?", style = SipType.Title)
            Text(
                "$BUDDY_NAME would see \"Becoatin · Taken 8:12 am\" or \"Not yet\". Nothing else about it. You can change this any time in Sharing.",
                style = SipType.Body.copy(color = Sip.Ice),
            )
            PrimaryButton("Yes, share", onClick = onYes, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onNo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Keep it private", style = SipType.Body.copy(color = Sip.Muted))
            }
        }
    }
}

/** His view of her medicine, when she shares it. */
@Composable
fun SharedMedsCard(meds: List<SharedMed>) {
    if (meds.isEmpty()) return
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Her medication", style = SipType.Section)
        meds.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.name, style = SipType.BodyStrong)
                    Text(m.takenAt?.let { "Taken ${timeLabel(it)}" } ?: "Not yet · due ${minuteLabel(m.dueMinute)}", style = SipType.Small)
                }
                if (m.takenAt != null) StatusPill("Taken", PillKind.Strong) else StatusPill("Not yet", PillKind.Behind)
            }
        }
    }
}
