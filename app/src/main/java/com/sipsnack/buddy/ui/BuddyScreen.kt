package com.sipsnack.buddy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipsnack.buddy.HER_NAME
import com.sipsnack.buddy.rules.Hydration
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.sync.DayDoc
import com.sipsnack.buddy.sync.Pairing
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private val cheers = listOf(
    "Let's have it! Keep going.",
    "Proud of you, Mini Boss Battle.",
    "Water break? Just one cup.",
    "You're smashing it today.",
)

/** His phone: her day, live, read-only. He can cheer; he can't change anything of hers. */
@Composable
fun BuddyScreen(vm: AppViewModel) {
    val pair by vm.pair.collectAsState()
    val me by vm.myUid.collectAsState()
    val week by vm.week.collectAsState()
    val problem by vm.linkProblem.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var cheering by remember { mutableStateOf(false) }

    val today = LocalDate.now().toString()
    val todayDoc = week.firstOrNull { it.date == today }
    val linked = pair?.buddyUid != null && pair?.buddyUid == me
    val live = linked && pair?.ownerUid != null && pair?.sharing == true && pair?.paused == false

    Column(
        Modifier.fillMaxSize().background(Sip.Ink).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Shared with you by", style = SipType.Small)
                Text(HER_NAME, style = SipType.Title)
            }
            if (live) StatusPill("Live", PillKind.Soft)
            IconButton(
                onClick = { menu = true },
                modifier = Modifier.size(48.dp).border(1.dp, Sip.Line, CircleShape).background(Sip.Night, CircleShape),
            ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Sip.Cloud) }
        }

        UpdateCard(vm)

        when {
            !Pairing.available -> Message("Sharing isn't set up in this version of the app.")
            problem != null && !linked -> Message(
                if (problem!!.contains("older install"))
                    "Can't link yet: $problem. If this stays, delete the pairs document in the Firebase console and reopen this app."
                else "Can't link yet: $problem. Retrying by itself."
            )
            pair == null || !linked -> Message("Linking to $HER_NAME's app...")
            pair?.ownerUid == null || pair?.sharing != true -> Message("Waiting for $HER_NAME to turn on sharing. Her app asks her the first time she opens it.")
            pair?.paused == true -> Message("$HER_NAME has paused sharing for now. It'll come back when she turns it on.")
            else -> {
                TodayCard(todayDoc)
                if (todayDoc != null && todayDoc.day.events.isNotEmpty()) HerDay(todayDoc)
                if (week.isNotEmpty()) WeekCard(week)
                PrimaryButton("Send a cheer", onClick = { cheering = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (cheering) {
        AlertDialog(
            onDismissRequest = { cheering = false },
            title = { Text("Send a cheer", style = SipType.Section) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cheers.forEach { line ->
                        Text(
                            line,
                            style = SipType.Body,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { vm.sendCheer(line); cheering = false }
                                .background(Sip.Night, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { cheering = false }) { Text("Cancel", color = Sip.Muted) } },
            containerColor = Sip.Deep,
        )
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("This phone", style = SipType.Section) },
            text = { Text("This phone is set up as her buddy. Picked the wrong one? Switching frees the buddy seat.", style = SipType.Body) },
            confirmButton = { TextButton(onClick = { vm.switchRole(); menu = false }) { Text("Switch phone role", color = Sip.Sky) } },
            dismissButton = { TextButton(onClick = { menu = false }) { Text("Close", color = Sip.Muted) } },
            containerColor = Sip.Deep,
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = SipType.Body.copy(color = Sip.Ice), modifier = Modifier.fillMaxWidth().card().padding(20.dp))
}

@Composable
private fun TodayCard(doc: DayDoc?) {
    val cups = doc?.day?.cups ?: 0.0
    val goal = doc?.day?.goal ?: 8
    val now = LocalTime.now()
    val behind = ceil(Hydration.expectedCups(goal, now.hour * 60 + now.minute, com.sipsnack.buddy.rules.DayWindow()) - cups).toInt()
    val shown = if (cups % 1.0 == 0.0) cups.toInt().toString() else "%.1f".format(cups)
    Row(
        Modifier.fillMaxWidth().card().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(132.dp).semantics { contentDescription = "$shown of $goal cups today" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val tl = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                drawArc(Sip.Deep, 0f, 360f, false, tl, arc, style = Stroke(stroke))
                drawArc(Sip.Electric, -90f, 360f * (cups / goal).toFloat().coerceIn(0f, 1f), false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$shown of $goal", style = SipType.Hero.copy(fontSize = 28.sp))
                Text("cups", style = SipType.Small)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                cups >= goal -> StatusPill("Goal hit", PillKind.Strong)
                behind >= 2 -> StatusPill("Behind · $behind cups", PillKind.Behind)
                else -> StatusPill("On pace", PillKind.Soft)
            }
            val left = ceil(goal - cups).toInt()
            Text(if (left <= 0) "All done today" else "$left ${if (left == 1) "cup" else "cups"} to go", style = SipType.Body.copy(fontWeight = FontWeight.Medium))
            Text(
                doc?.day?.lastSipMillis?.let { "Last sip ${timeOf(it)}" } ?: "No sips logged yet today",
                style = SipType.Small,
            )
        }
    }
}

@Composable
private fun HerDay(doc: DayDoc) {
    Column {
        Text("Her day", style = SipType.Section, modifier = Modifier.padding(bottom = 6.dp))
        doc.day.events.sortedByDescending { it.atMillis }.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = Sip.Deep)
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(timeOf(e.atMillis), style = SipType.Small, modifier = Modifier.width(72.dp))
                Text(
                    when (e.kind) { LogKind.WATER -> "Water"; LogKind.OTHER_DRINK -> "Other drink"; LogKind.MEAL -> "Meal"; LogKind.SNACK -> "Snack" },
                    style = SipType.Body,
                    modifier = Modifier.weight(1f),
                )
                if (e.kind == LogKind.WATER) {
                    val n = if (e.cups % 1.0 == 0.0) e.cups.toInt().toString() else e.cups.toString()
                    Text("+$n ${if (e.cups == 1.0) "cup" else "cups"}", style = SipType.Small.copy(color = Sip.Ice))
                }
            }
        }
        Text("Times and amounts only. No photos are ever shared.", style = SipType.Small.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WeekCard(week: List<DayDoc>) {
    val days = week.sortedBy { it.date }
    val hit = days.count { it.day.cups >= it.day.goal }
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("This week", style = SipType.Section, modifier = Modifier.weight(1f))
            Text("Goal hit $hit of ${days.size} days", style = SipType.Small)
        }
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val done = d.day.cups >= d.day.goal
                val frac = (d.day.cups / d.day.goal).toFloat().coerceIn(0.08f, 1f)
                val isToday = d.date == LocalDate.now().toString()
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val bar = Modifier.fillMaxWidth().height((80 * frac).dp)
                    Box(
                        if (done) bar.background(Sip.Electric, RoundedCornerShape(8.dp))
                        else bar.background(Sip.Deep, RoundedCornerShape(8.dp)).drawBehind {
                            drawRoundRect(Sip.Sky, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                        },
                    )
                    Text(
                        if (isToday) "Today" else LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = SipType.Small.copy(fontSize = 12.sp, color = if (isToday) Sip.Cloud else Sip.Muted),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Text("Solid bar: goal hit. Dashed: under goal.", style = SipType.Small.copy(fontSize = 12.sp))
    }
}

private fun timeOf(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))
