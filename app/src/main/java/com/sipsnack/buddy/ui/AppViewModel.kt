package com.sipsnack.buddy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sipsnack.buddy.data.AppDb
import com.sipsnack.buddy.data.LogRow
import com.sipsnack.buddy.data.Prefs
import com.sipsnack.buddy.memes.Memes
import com.sipsnack.buddy.reminders.startMillis
import com.sipsnack.buddy.rules.Hydration
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.rules.Moment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).logs()
    val prefs = Prefs(app)

    /** Re-checks the date every minute so the day rolls over at midnight. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayLogs: StateFlow<List<LogRow>> = today
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
        .flatMapLatest { day -> dao.between(day.startMillis(), day.plusDays(1).startMillis()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The meme card on screen, if any. */
    val moment = MutableStateFlow<Moment?>(null)

    /** A small "Let's have it!" banner for ordinary cups. */
    val toast = MutableStateFlow<String?>(null)

    val goal = MutableStateFlow(prefs.goal)
    val memesOn = MutableStateFlow(prefs.memesOn)
    val nudgesOn = MutableStateFlow(prefs.nudgesOn)

    fun cupsNow(): Double = Hydration.cups(todayLogs.value.map { it.toEntry() })

    fun log(kind: LogKind, cups: Double, label: String) {
        val before = cupsNow()
        viewModelScope.launch {
            dao.insert(LogRow(atMillis = System.currentTimeMillis(), kind = kind.name, cups = cups, label = label))
        }
        if (kind != LogKind.WATER) {
            toast.value = "$label logged."
            return
        }
        val reached = Hydration.momentForLog(before, before + cups, goal.value)
        celebrate(reached)
    }

    private fun celebrate(reached: Moment) {
        if (!memesOn.value) {
            toast.value = if (reached == Moment.GOAL_HIT) "Goal hit. Proud of you!" else "Logged."
            return
        }
        if (reached == Moment.CUP_LOGGED) {
            Memes.forMoment(reached).sound?.let { Memes.playSound(getApplication(), it) }
            toast.value = "Let's have it! Cup logged."
        } else {
            moment.value = reached
        }
    }

    fun show(moment: Moment) {
        if (memesOn.value) this.moment.value = moment
    }

    fun dismissMoment() { moment.value = null }

    fun dismissToast() { toast.value = null }

    fun delete(row: LogRow) {
        viewModelScope.launch { dao.delete(row.id) }
    }

    fun deleteAll() {
        viewModelScope.launch { dao.deleteAll() }
    }

    fun setGoal(value: Int) {
        prefs.goal = value
        goal.value = prefs.goal
    }

    fun setMemesOn(value: Boolean) {
        prefs.memesOn = value
        memesOn.value = value
    }

    fun setNudgesOn(value: Boolean) {
        prefs.nudgesOn = value
        nudgesOn.value = value
    }
}
