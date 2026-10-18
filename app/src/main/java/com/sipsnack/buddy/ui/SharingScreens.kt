package com.sipsnack.buddy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sipsnack.buddy.BUDDY_NAME
import com.sipsnack.buddy.HER_NAME
import com.sipsnack.buddy.rules.ShareLevel
import com.sipsnack.buddy.sync.Pairing
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType

/** First open: one question so a single app works on both phones. */
@Composable
fun RoleScreen(onChoose: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Sip.Ink).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Welcome to Sip & Snack", style = SipType.Small)
        Text("Who's using this phone?", style = SipType.Title)
        Text("You only answer this once.", style = SipType.Body.copy(color = Sip.Muted))
        Spacer(Modifier.size(8.dp))
        PrimaryButton(HER_NAME, onClick = { onChoose("her") }, modifier = Modifier.fillMaxWidth())
        OutlineButton(BUDDY_NAME, onClick = { onChoose("buddy") }, modifier = Modifier.fillMaxWidth())
    }
}

/** The one-tap ask. Shown on her phone on first open, and once more a week after a "not now". */
@Composable
fun ShareAskCard(onYes: () -> Unit, onNotNow: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Sip.Ink.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.fillMaxWidth().card().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Let $BUDDY_NAME cheer you on?", style = SipType.Title)
            Text(
                "$BUDDY_NAME sees your water progress and meal times live. Never your photos. You can pause or stop it any time in Settings.",
                style = SipType.Body.copy(color = Sip.Ice),
            )
            PrimaryButton("Yes, share with him", onClick = onYes, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Not now", style = SipType.Body.copy(color = Sip.Muted))
            }
        }
    }
}

/** Her sharing controls. Always reachable from the label on her Today screen. */
@Composable
fun SharingScreen(vm: AppViewModel, onBack: () -> Unit) {
    val pair by vm.pair.collectAsState()
    val me by vm.myUid.collectAsState()
    val mine = pair?.ownerUid != null && pair?.ownerUid == me
    val sharing = mine && pair?.sharing == true
    val paused = pair?.paused == true
    var confirmStop by remember { mutableStateOf(false) }
    val problem by vm.linkProblem.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Sip.Ink).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header("Sharing", onBack)

        if (!Pairing.available) {
            Text("Sharing isn't set up in this version of the app.", style = SipType.Body.copy(color = Sip.Muted))
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().card().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(12.dp).background(if (sharing && !paused) Sip.Electric else Sip.Muted, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        !sharing -> "Not sharing"
                        paused -> "Paused"
                        else -> "Sharing with $BUDDY_NAME"
                    },
                    style = SipType.BodyStrong,
                )
                Text(
                    when {
                        !sharing -> "$BUDDY_NAME can't see anything right now."
                        paused -> "$BUDDY_NAME sees \"Paused\"."
                        pair?.buddyUid == null -> "Live. He'll see it once his phone is linked."
                        else -> "Live. Updates every time you log."
                    },
                    style = SipType.Small,
                )
            }
        }

        if (!sharing) {
            problem?.let { Text("Not connected yet: $it. It keeps trying by itself.", style = SipType.Small) }
            PrimaryButton("Share with $BUDDY_NAME", onClick = vm::startSharing, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What he sees", style = SipType.Section)
            LevelOption("Progress only", "Cups, goal and last sip time", pair?.level == ShareLevel.PROGRESS) { vm.setShareLevel(ShareLevel.PROGRESS) }
            LevelOption("Progress and meal times", "Also when you ate. Never photos.", pair?.level == ShareLevel.WITH_MEALS) { vm.setShareLevel(ShareLevel.WITH_MEALS) }
        }

        Row(
            Modifier.fillMaxWidth().card().heightIn(min = 64.dp)
                .toggleable(paused, role = Role.Switch, onValueChange = vm::setSharePaused).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pause sharing", style = SipType.BodyStrong)
                Text("He sees \"Paused\" until you turn it back on", style = SipType.Small)
            }
            Switch(
                checked = paused,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Sip.Electric, checkedThumbColor = Sip.Ink, uncheckedTrackColor = Sip.Deep, uncheckedThumbColor = Sip.Muted, uncheckedBorderColor = Sip.Line),
            )
        }

        Row(
            Modifier.fillMaxWidth().card().heightIn(min = 64.dp)
                .toggleable(pair?.allowAdds != false, role = Role.Switch, onValueChange = vm::setAllowAdds).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Let him log for you", style = SipType.BodyStrong)
                Text("When you're together, $BUDDY_NAME can add sips and meals to your day. They show \"by $BUDDY_NAME\" and you can remove them.", style = SipType.Small)
            }
            Switch(
                checked = pair?.allowAdds != false,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Sip.Electric, checkedThumbColor = Sip.Ink, uncheckedTrackColor = Sip.Deep, uncheckedThumbColor = Sip.Muted, uncheckedBorderColor = Sip.Line),
            )
        }

        TextButton(onClick = { confirmStop = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Stop sharing and delete the shared copy", style = SipType.Body.copy(color = Sip.Sky))
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop sharing?", style = SipType.Section) },
            text = { Text("$BUDDY_NAME stops seeing your progress and the shared copy is deleted. Your own logs stay on your phone.", style = SipType.Body) },
            confirmButton = { TextButton(onClick = { vm.stopSharing(); confirmStop = false }) { Text("Stop sharing", color = Sip.Sky) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep sharing", color = Sip.Muted) } },
            containerColor = Sip.Deep,
        )
    }
}

@Composable
private fun LevelOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (selected) Sip.Deep else Sip.Night, RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Sip.Electric else Sip.Line, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = SipType.BodyStrong)
        Text(detail, style = SipType.Small)
    }
}

@Composable
fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Sip.Cloud)
        }
        Text(title, style = SipType.Title)
    }
}
