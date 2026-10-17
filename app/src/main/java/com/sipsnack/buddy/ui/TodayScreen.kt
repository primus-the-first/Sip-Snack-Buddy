package com.sipsnack.buddy.ui

import com.sipsnack.buddy.HER_NAME
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.data.LogRow
import com.sipsnack.buddy.rules.Hydration
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
fun TodayScreen(vm: AppViewModel, onSnap: () -> Unit, onSettings: () -> Unit) {
    val logs by vm.todayLogs.collectAsState()
    val goal by vm.goal.collectAsState()
    val cups = Hydration.cups(logs.map { it.toEntry() })
    val now = LocalTime.now()
    val behind = ceil(Hydration.expectedCups(goal, now.hour * 60 + now.minute, vm.prefs.window) - cups).toInt()
    var confirmDelete by remember { mutableStateOf<LogRow?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sip.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = SipType.Small)
                Text(greeting(now), style = SipType.Title)
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(48.dp).border(1.dp, Sip.Line, CircleShape).background(Sip.Night, CircleShape),
            ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Sip.Cloud) }
        }

        Column(
            Modifier.fillMaxWidth().card().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProgressRing(cups = cups, goal = goal)
            Text(message(cups, goal), style = SipType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            when {
                cups >= goal -> StatusPill("Goal hit", PillKind.Strong)
                behind >= 2 -> StatusPill("Behind · $behind cups", PillKind.Behind)
                else -> StatusPill("On pace", PillKind.Soft)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Snap a sip", onClick = onSnap, modifier = Modifier.weight(1f))
            OutlineButton("+1 cup", onClick = { vm.log(LogKind.WATER, 1.0, "Water") }, modifier = Modifier.width(120.dp))
        }

        Column {
            Text("Today so far", style = SipType.Section, modifier = Modifier.padding(bottom = 6.dp))
            if (logs.isEmpty()) {
                Text("Nothing yet. Your first sip starts the ring.", style = SipType.Small, modifier = Modifier.padding(vertical = 12.dp))
            }
            logs.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = Sip.Deep)
                LogLine(row, onClick = { confirmDelete = row })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove this?", style = SipType.Section) },
            text = { Text("${row.label} at ${timeOf(row.atMillis)}. Mistakes happen.", style = SipType.Body) },
            confirmButton = { TextButton(onClick = { vm.delete(row); confirmDelete = null }) { Text("Remove", color = Sip.Sky) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep it", color = Sip.Muted) } },
            containerColor = Sip.Deep,
        )
    }
}

@Composable
private fun ProgressRing(cups: Double, goal: Int) {
    val target = (cups / goal).toFloat().coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, animationSpec = tween(700), label = "ring")
    val shown = if (cups % 1.0 == 0.0) cups.toInt().toString() else "%.1f".format(cups)
    Box(
        Modifier.size(240.dp).semantics { contentDescription = "$shown of $goal cups today" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Sip.Deep, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(Sip.Electric, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shown of $goal", style = SipType.Hero)
            Text("cups today", style = SipType.Small.copy(fontSize = SipType.Body.fontSize))
        }
    }
}

@Composable
private fun LogLine(row: LogRow, onClick: () -> Unit) {
    val kind = LogKind.valueOf(row.kind)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(timeOf(row.atMillis), style = SipType.Small, modifier = Modifier.width(72.dp))
        Text(row.label, style = SipType.Body, modifier = Modifier.weight(1f))
        val right = when (kind) {
            LogKind.WATER -> "+${if (row.cups % 1.0 == 0.0) row.cups.toInt() else row.cups} ${if (row.cups == 1.0) "cup" else "cups"}"
            LogKind.OTHER_DRINK -> "Drink"
            LogKind.MEAL -> "Meal"
            LogKind.SNACK -> "Snack"
        }
        Text(right, style = SipType.Small.copy(color = if (kind == LogKind.WATER) Sip.Ice else Sip.Muted))
    }
}

private fun timeOf(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))

private fun greeting(now: LocalTime) = when (now.hour) {
    in 5..11 -> "Good morning, $HER_NAME"
    in 12..16 -> "Good afternoon, $HER_NAME"
    else -> "Good evening, $HER_NAME"
}

private fun message(cups: Double, goal: Int): String {
    val left = ceil(goal - cups).toInt()
    return when {
        left <= 0 -> "All $goal done. Proud of you!"
        left == 1 -> "1 cup to go. Nearly there."
        cups == 0.0 -> "$goal cups today. First sip whenever you're ready."
        else -> "$left cups to go. You've got this."
    }
}
