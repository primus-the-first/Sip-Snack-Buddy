package com.sipsnack.buddy.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipsnack.buddy.rules.DayStats
import com.sipsnack.buddy.rules.Growth
import com.sipsnack.buddy.rules.History
import com.sipsnack.buddy.rules.Units
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

/** Past days, to see how things are going: water, steps and medicine over 7 or 30 days, compared with the days before. */
@Composable
fun HistoryScreen(vm: AppViewModel, hers: Boolean, onBack: () -> Unit) {
    LaunchedEffect(hers) { vm.loadHistory(hers) }
    val byDate by vm.history.collectAsState()
    val problem by vm.historyProblem.collectAsState()
    var span by rememberSaveable { mutableIntStateOf(7) }
    val today = LocalDate.now()

    Column(
        Modifier.fillMaxSize().background(Sip.Ink).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                @Suppress("DEPRECATION")
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Sip.Cloud)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (hers) "${com.sipsnack.buddy.HER_NAME}'s history" else "My history", style = SipType.Title)
                Text("How the days are adding up", style = SipType.Small)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpanChip("7 days", span == 7, Modifier.weight(1f)) { span = 7 }
            SpanChip("30 days", span == 30, Modifier.weight(1f)) { span = 30 }
        }

        val days = byDate
        when {
            days == null -> Text("Loading...", style = SipType.Small)
            problem != null && days.isEmpty() -> Text("Couldn't load the history: $problem. Try again in a minute.", style = SipType.Small)
            days.isEmpty() -> Text("Nothing recorded yet. Each day you log shows up here.", style = SipType.Small)
            else -> {
                val shown = History.range(days, today, span)
                val growth = History.growth(days, today, span)
                WaterCard(shown, growth, span)
                if (shown.any { it.second?.steps != null }) StepsHistoryCard(shown, growth, span)
                if (growth.current.medDays > 0) MedsHistoryCard(growth, span)
                Text("Averages leave out today, since it isn't over yet.", style = SipType.Small.copy(fontSize = 12.sp))
                DayList(shown.reversed())
            }
        }
    }
}

@Composable
private fun SpanChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier.heightIn(min = 48.dp)
            .then(if (selected) Modifier.background(Sip.Deep, shape).border(2.dp, Sip.Electric, shape) else Modifier.border(1.dp, Sip.Line, shape))
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = SipType.Label.copy(color = if (selected) Sip.Cloud else Sip.Muted)) }
}

@Composable
private fun WaterCard(shown: List<Pair<String, DayStats?>>, growth: Growth, span: Int) {
    val now = growth.current
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Water", style = SipType.Section)
        Text(now.avgCups?.let { "${Units.ml(it)} a day" } ?: "No full days yet", style = SipType.Title)
        Text(change(growth.waterChange, span), style = SipType.Body)
        if (now.recordedDays > 0) Text("Goal hit ${now.waterGoalDays} of ${now.recordedDays} days", style = SipType.Small)
        Bars(shown.map { (date, d) -> Bar(date, d?.let { (it.cups / it.goal).toFloat() }, d != null && d.cups >= d.goal) }, "water")
    }
}

@Composable
private fun StepsHistoryCard(shown: List<Pair<String, DayStats?>>, growth: Growth, span: Int) {
    val now = growth.current
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Steps", style = SipType.Section)
        Text(now.avgSteps?.let { "${"%,d".format(Locale.US, it)} a day" } ?: "No full days yet", style = SipType.Title)
        Text(change(growth.stepsChange, span), style = SipType.Body)
        if (now.stepDays > 0) Text("Goal hit ${now.stepGoalDays} of ${now.stepDays} days", style = SipType.Small)
        Bars(
            shown.map { (date, d) ->
                val goal = d?.stepGoal ?: 5_000
                Bar(date, d?.steps?.let { it.toFloat() / goal }, d?.steps != null && d.steps >= goal)
            },
            "steps",
        )
    }
}

@Composable
private fun MedsHistoryCard(growth: Growth, span: Int) {
    val now = growth.current
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Medicine", style = SipType.Section)
        Text("Taken ${now.medTakenDays} of ${now.medDays} days", style = SipType.Title)
        Text("Over the last $span days, counted from when medicine tracking began.", style = SipType.Small)
    }
}

/** "Up 12% on the 7 days before" in words, so it never relies on colour. */
private fun change(percent: Int?, span: Int): String = when {
    percent == null -> "Not enough earlier days to compare yet"
    percent == 0 -> "Same as the $span days before"
    percent > 0 -> "Up $percent% on the $span days before"
    else -> "Down ${percent.absoluteValue}% on the $span days before"
}

private class Bar(val date: String, val fraction: Float?, val hit: Boolean)

/** Solid bar: goal hit. Dashed: under goal. Nothing: no record that day. */
@Composable
private fun Bars(bars: List<Bar>, what: String) {
    val hits = bars.count { it.hit }
    val recorded = bars.count { it.fraction != null }
    val gap = if (bars.size > 10) 2.dp else 8.dp
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$what goal hit on $hits of $recorded recorded days" }) {
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
            bars.forEach { b ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    val f = b.fraction ?: return@Box
                    val shape = RoundedCornerShape(if (bars.size > 10) 3.dp else 8.dp)
                    val bar = Modifier.fillMaxWidth().fillMaxHeight(f.coerceIn(0.06f, 1f))
                    Box(
                        if (b.hit) bar.background(Sip.Electric, shape)
                        else bar.background(Sip.Deep, shape).drawBehind {
                            drawRoundRect(Sip.Sky, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                        },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(shortDate(bars.first().date), style = SipType.Small.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
            Text("Today", style = SipType.Small.copy(fontSize = 12.sp, color = Sip.Cloud))
        }
        Text("Solid bar: goal hit. Dashed: under goal. Gap: nothing recorded.", style = SipType.Small.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DayList(days: List<Pair<String, DayStats?>>) {
    Column {
        Text("Day by day", style = SipType.Section, modifier = Modifier.padding(bottom = 6.dp))
        days.forEachIndexed { index, (date, d) ->
            if (index > 0) HorizontalDivider(color = Sip.Deep)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (date == LocalDate.now().toString()) "Today" else shortDate(date, withDay = true),
                    style = SipType.Label,
                    modifier = Modifier.weight(0.38f),
                )
                Column(Modifier.weight(0.62f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (d == null) {
                        Text("Nothing recorded", style = SipType.Small)
                    } else {
                        Text(Units.ml(d.cups) + if (d.cups >= d.goal) " · goal hit" else "", style = SipType.Body.copy(color = Sip.Ice))
                        val extras = listOfNotNull(
                            d.steps?.let { "${"%,d".format(Locale.US, it)} steps" },
                            d.meals?.let { if (it == 1) "1 meal" else "$it meals" },
                            d.medsTaken?.let { if (it) "Medicine taken" else "Medicine missed" },
                        )
                        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = SipType.Small)
                    }
                }
            }
        }
    }
}

private fun shortDate(date: String, withDay: Boolean = false): String =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern(if (withDay) "EEE d MMM" else "d MMM", Locale.getDefault()))
