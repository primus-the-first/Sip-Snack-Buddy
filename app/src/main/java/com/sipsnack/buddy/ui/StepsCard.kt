package com.sipsnack.buddy.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.steps.StepSensor
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType
import java.util.Locale

/** Today's steps under the water ring. Hidden on phones without a step sensor. */
@Composable
fun StepsCard(vm: AppViewModel) {
    val context = LocalContext.current
    if (!StepSensor.available(context)) return
    val steps by vm.steps.collectAsState()
    val goal by vm.stepGoal.collectAsState()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshSteps()
        StepSensor.keepCounting(context)
    }

    if (!StepSensor.allowed(context)) {
        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Steps", style = SipType.Section)
            Text("Count your steps with your phone's own step sensor. Nothing leaves your phone unless you share it.", style = SipType.Small)
            OutlineButton("Count my steps", onClick = { if (Build.VERSION.SDK_INT >= 29) ask.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
        }
        return
    }
    StepsBar(steps ?: 0, goal)
}

@Composable
fun StepsBar(steps: Int, goal: Int, modifier: Modifier = Modifier) {
    val done = steps >= goal
    Column(
        modifier.fillMaxWidth().card().padding(20.dp).semantics { contentDescription = "$steps of $goal steps today" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Steps", style = SipType.Section, modifier = Modifier.weight(1f))
            if (done) StatusPill("Goal hit", PillKind.Strong)
        }
        Text("${"%,d".format(Locale.US, steps)} of ${"%,d".format(Locale.US, goal)}", style = SipType.Title)
        Box(Modifier.fillMaxWidth().height(12.dp).background(Sip.Deep, RoundedCornerShape(999.dp))) {
            Box(
                Modifier.fillMaxWidth((steps.toFloat() / goal).coerceIn(0f, 1f)).fillMaxHeight()
                    .background(Sip.Electric, RoundedCornerShape(999.dp)),
            )
        }
    }
}
