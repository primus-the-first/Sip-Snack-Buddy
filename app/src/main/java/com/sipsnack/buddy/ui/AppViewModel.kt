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
import com.sipsnack.buddy.BUDDY_NAME
import com.sipsnack.buddy.rules.ShareLevel
import com.sipsnack.buddy.rules.Sharing
import com.sipsnack.buddy.sync.DayDoc
import com.sipsnack.buddy.sync.PairState
import com.sipsnack.buddy.sync.Pairing
import com.sipsnack.buddy.sync.PairingException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    /** Whose phone this is; null until the first-run question is answered. */
    val role = MutableStateFlow(prefs.role)

    /** The shared pair, live, on both phones. Null when Firebase isn't set up or nothing exists yet. */
    val pair: StateFlow<PairState?> = Pairing.observePair().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Her last 7 shared days, re-listened whenever she pauses or resumes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val week: StateFlow<List<DayDoc>> = pair
        .map { it?.sharing == true && !it.paused }
        .distinctUntilChanged()
        .flatMapLatest { Pairing.observeWeek() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myUid = MutableStateFlow<String?>(null)

    /** The one-tap "Let Walking Selfie Stick cheer you on?" card. */
    val askShare = MutableStateFlow(prefs.role == "her" && Pairing.available && prefs.shouldAskToShare())

    val goal = MutableStateFlow(prefs.goal)
    val memesOn = MutableStateFlow(prefs.memesOn)
    val nudgesOn = MutableStateFlow(prefs.nudgesOn)

    init {
        if (Pairing.available) viewModelScope.launch { myUid.value = runCatching { Pairing.uid() }.getOrNull() }
        syncHerDay()
        watchCheers()
    }

    /** On her phone, every change to today's logs reaches her buddy a moment later. */
    @OptIn(FlowPreview::class)
    private fun syncHerDay() {
        viewModelScope.launch {
            combine(todayLogs, pair, goal, myUid) { logs, p, g, me -> Triple(logs, p, g) to me }
                .debounce(1500)
                .collect { (state, me) ->
                    val (logs, p, g) = state
                    if (role.value != "her" || p == null || me == null || p.ownerUid != me || !p.sharing || p.paused) return@collect
                    val day = Sharing.summarise(logs.map { it.toEntry() }, g, p.level)
                    runCatching { Pairing.pushDay(LocalDate.now().toString(), day) }
                }
        }
    }

    /** A cheer from her buddy shows as a banner while the app is open. */
    private fun watchCheers() {
        viewModelScope.launch {
            pair.collect { p ->
                if (role.value == "her" && p?.cheer != null && p.cheerAt > prefs.lastCheerSeenAt) {
                    prefs.lastCheerSeenAt = p.cheerAt
                    toast.value = "$BUDDY_NAME: ${p.cheer}"
                }
            }
        }
    }

    fun chooseRole(newRole: String) {
        prefs.role = newRole
        role.value = newRole
        if (newRole == "buddy") {
            viewModelScope.launch {
                runCatching { Pairing.claimBuddy() }.onFailure {
                    if (it.message != "already") toast.value = "Couldn't link up: someone else already has the buddy seat."
                }
            }
        } else {
            askShare.value = Pairing.available && prefs.shouldAskToShare()
        }
    }

    /** For a phone that picked the wrong role: frees its seat and asks again. */
    fun switchRole() {
        val me = myUid.value
        val p = pair.value
        viewModelScope.launch {
            runCatching {
                if (role.value == "her" && p?.ownerUid == me) Pairing.releaseOwner()
                if (role.value == "buddy" && p?.buddyUid == me) Pairing.releaseBuddy()
            }
        }
        prefs.role = null
        role.value = null
    }

    fun acceptShare() {
        prefs.shareAnswer = "yes"
        askShare.value = false
        startSharing()
    }

    fun declineShare() {
        prefs.shareAnswer = "no"
        prefs.shareAskedCount = prefs.shareAskedCount + 1
        prefs.shareAskedAt = System.currentTimeMillis()
        askShare.value = false
    }

    fun startSharing() {
        viewModelScope.launch {
            runCatching { Pairing.claimHer(pair.value?.level ?: ShareLevel.WITH_MEALS) }.onFailure {
                toast.value = when (it) {
                    is PairingException -> "Couldn't turn on sharing: another phone holds your seat."
                    else -> "Couldn't turn on sharing. Check your internet and try again."
                }
            }
        }
    }

    fun setShareLevel(level: ShareLevel) {
        val p = pair.value ?: return
        viewModelScope.launch { runCatching { Pairing.setSharing(true, p.paused, level) } }
    }

    fun setSharePaused(paused: Boolean) {
        val p = pair.value ?: return
        viewModelScope.launch { runCatching { Pairing.setSharing(true, paused, p.level) } }
    }

    fun stopSharing() {
        prefs.shareAnswer = "stopped"
        viewModelScope.launch {
            runCatching { Pairing.stopAndDelete() }
                .onSuccess { toast.value = "Sharing stopped. The shared copy is deleted." }
                .onFailure { toast.value = "Couldn't stop sharing. Check your internet and try again." }
        }
    }

    fun sendCheer(text: String) {
        viewModelScope.launch {
            runCatching { Pairing.sendCheer(text) }
                .onSuccess { toast.value = "Cheer sent." }
                .onFailure { toast.value = "Couldn't send. Check your internet." }
        }
    }

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
