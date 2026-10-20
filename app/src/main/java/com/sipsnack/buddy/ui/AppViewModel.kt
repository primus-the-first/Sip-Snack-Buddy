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
import com.sipsnack.buddy.sync.reason
import kotlinx.coroutines.Job
import com.sipsnack.buddy.update.Release
import com.sipsnack.buddy.HER_NAME
import com.sipsnack.buddy.rules.Units
import com.sipsnack.buddy.sync.AddedLog
import com.sipsnack.buddy.sync.Importer
import com.sipsnack.buddy.steps.StepSensor
import com.sipsnack.buddy.rules.Med
import com.sipsnack.buddy.rules.Meds
import com.sipsnack.buddy.sync.HerDay
import kotlinx.coroutines.flow.merge
import com.sipsnack.buddy.update.Updater
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
import java.time.ZoneId
import com.sipsnack.buddy.rules.DayStats
import com.sipsnack.buddy.rules.History

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

    /** Her last 7 shared days, re-listened when she pauses or resumes and when the date changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val week: StateFlow<List<DayDoc>> = combine(pair.map { it?.sharing == true && !it.paused }, today) { live, day -> live to day }
        .distinctUntilChanged()
        .flatMapLatest { (_, day) -> Pairing.observeWeek(day.minusDays(6).toString()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myUid = MutableStateFlow<String?>(null)

    /** Who this phone's own Today screen belongs to. */
    val myName: String get() = if (role.value == "buddy") BUDDY_NAME else HER_NAME

    /** On his phone: logs he added for her that her phone hasn't taken in yet. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val waitingForHer: StateFlow<List<AddedLog>> = combine(role, pair, myUid) { r, p, me ->
        r == "buddy" && me != null && p?.buddyUid == me
    }.distinctUntilChanged()
        .flatMapLatest { mine -> if (mine) Pairing.observeAdded() else flow { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** A newer release on GitHub, offered as a one-tap update. */
    val update = MutableStateFlow<Release?>(null)
    val updating = MutableStateFlow(false)
    private var lastUpdateCheck = 0L

    /** Why this phone isn't linked yet, in plain words; null when linked or not trying. */
    val linkProblem = MutableStateFlow<String?>(null)
    private var linking: Job? = null

    /** The one-tap "Let Walking Selfie Stick cheer you on?" card. */
    val askShare = MutableStateFlow(prefs.role == "her" && Pairing.available && prefs.shouldAskToShare())

    val goal = MutableStateFlow(prefs.goal)

    /** Today's steps on this phone; null when it can't count them (no sensor, or not allowed yet). */
    val steps = MutableStateFlow<Int?>(null)
    val stepGoal = MutableStateFlow(prefs.stepGoal)
    val shareSteps = MutableStateFlow(prefs.shareSteps)
    val stepsAllDay = MutableStateFlow(prefs.stepsAllDay)

    /** Her medicines and the last two months of check-offs (for today's status and streaks). */
    val meds = MutableStateFlow(prefs.meds)
    val medLogs: StateFlow<List<LogRow>> = dao.medsSince(LocalDate.now().minusDays(60).startMillis())
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shareMeds = MutableStateFlow(prefs.shareMeds)
    val memesOn = MutableStateFlow(prefs.memesOn)

    /** History by date: 60 days, so 30 can be shown against the 30 before. Null while loading. */
    val history = MutableStateFlow<Map<String, DayStats>?>(null)
    val historyProblem = MutableStateFlow<String?>(null)
    val nudgesOn = MutableStateFlow(prefs.nudgesOn)

    init {
        if (Pairing.available) viewModelScope.launch { myUid.value = runCatching { Pairing.uid() }.getOrNull() }
        syncHerDay()
        watchCheers()
        seedMeds()
        takeHisLogs()
        countSteps()
        autoLink()
    }

    /** Becoatin, once a day at 8:00 am, the first time her phone runs this version. She can change it in Settings. */
    private fun seedMeds() {
        if (role.value == "her" && !prefs.medsSeeded) {
            prefs.meds = listOf(Med(id = "becoatin", name = "Becoatin", minuteOfDay = 8 * 60))
            prefs.medsSeeded = true
            meds.value = prefs.meds
        }
    }

    fun takenToday(med: Med): Long? {
        val start = LocalDate.now().startMillis()
        return medLogs.value.filter { it.label == med.name && it.atMillis >= start }.maxOfOrNull { it.atMillis }
    }

    fun streak(med: Med): Int {
        val zone = java.time.ZoneId.systemDefault()
        val days = medLogs.value.filter { it.label == med.name }
            .map { java.time.Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }.toSet()
        return Meds.streak(days, LocalDate.now())
    }

    fun takeMed(med: Med) {
        if (takenToday(med) != null) return
        viewModelScope.launch {
            dao.insert(LogRow(atMillis = System.currentTimeMillis(), kind = LogKind.MED.name, cups = 0.0, label = med.name))
            val zone = java.time.ZoneId.systemDefault()
            val taken = medLogs.value.filter { it.label == med.name }
                .map { java.time.Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }.toSet() + LocalDate.now()
            val days = Meds.streak(taken, LocalDate.now())
            if (memesOn.value) Memes.forMoment(Moment.CUP_LOGGED).sound?.let { Memes.playSound(getApplication(), it) }
            toast.value = if (days >= 2) "${med.name} taken. $days days in a row!" else "${med.name} taken. Let's have it!"
        }
    }

    fun addMed(name: String, minuteOfDay: Int) {
        val clean = name.trim().take(40).ifEmpty { return }
        prefs.meds = prefs.meds + Med(id = "m" + System.currentTimeMillis(), name = clean, minuteOfDay = minuteOfDay)
        meds.value = prefs.meds
    }

    fun removeMed(id: String) {
        prefs.meds = prefs.meds.filterNot { it.id == id }
        meds.value = prefs.meds
    }

    fun setMedTime(id: String, minuteOfDay: Int) {
        prefs.meds = prefs.meds.map { if (it.id == id) it.copy(minuteOfDay = minuteOfDay) else it }
        meds.value = prefs.meds
    }

    fun setShareMeds(value: Boolean) {
        prefs.shareMeds = value
        shareMeds.value = value
    }

    /** Reads the step counter every half minute while the app is open. */
    private fun countSteps() {
        viewModelScope.launch {
            while (true) {
                refreshSteps()
                delay(30_000)
            }
        }
    }

    fun refreshSteps() {
        viewModelScope.launch {
            val now = StepSensor.todaySteps(getApplication()) ?: return@launch
            val before = steps.value
            steps.value = now
            val today = LocalDate.now().toString()
            if (before != null && before < stepGoal.value && now >= stepGoal.value && prefs.stepGoalCelebratedDay != today) {
                prefs.stepGoalCelebratedDay = today
                if (memesOn.value) Memes.forMoment(Moment.CUP_LOGGED).sound?.let { Memes.playSound(getApplication(), it) }
                toast.value = "Let's have it! ${"%,d".format(java.util.Locale.US, now)} steps today."
            }
        }
    }

    /** [hers]: her shared days on his phone. Otherwise this phone's own logs and steps. */
    fun loadHistory(hers: Boolean) {
        history.value = null
        historyProblem.value = null
        viewModelScope.launch {
            val from = LocalDate.now().minusDays(60)
            runCatching {
                if (hers) {
                    Pairing.readDays(from.toString()).associate { it.date to it.toStats() }
                } else {
                    val logs = dao.betweenNow(from.startMillis(), LocalDate.now().plusDays(1).startMillis()).map { it.toEntry() }
                    History.fromLogs(logs, ZoneId.systemDefault(), prefs.goal, prefs.stepGoal, prefs.stepHistory, tracksMeds = prefs.meds.isNotEmpty())
                }
            }.onSuccess { history.value = it }
                .onFailure { historyProblem.value = it.reason(); history.value = emptyMap() }
        }
    }

    private fun DayDoc.toStats() = DayStats(
        date = date,
        cups = day.cups,
        goal = day.goal,
        steps = day.steps,
        stepGoal = day.stepGoal,
        meals = day.events.takeIf { it.isNotEmpty() }?.count { it.kind == LogKind.MEAL },
        medsTaken = day.meds?.takeIf { it.isNotEmpty() }?.all { it.takenAt != null },
    )

    fun setStepGoal(value: Int) {
        prefs.stepGoal = value
        stepGoal.value = prefs.stepGoal
    }

    fun setStepsAllDay(value: Boolean) {
        prefs.stepsAllDay = value
        stepsAllDay.value = value
        if (value) StepSensor.keepCounting(getApplication()) else StepSensor.stopCounting(getApplication())
    }

    fun setShareSteps(value: Boolean) {
        prefs.shareSteps = value
        shareSteps.value = value
    }

    /** On her phone, logs her buddy added land in her day while the app is open (the worker covers the rest). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun takeHisLogs() {
        viewModelScope.launch {
            combine(role, pair, myUid) { r, p, me -> r == "her" && me != null && p?.ownerUid == me && p.sharing }
                .distinctUntilChanged()
                .flatMapLatest { mine -> if (mine) Pairing.observeAdded() else flow { emit(emptyList()) } }
                .collect { waiting ->
                    val taken = Importer.take(getApplication(), waiting)
                    if (taken > 0) {
                        val total = waiting.filter { it.kind == LogKind.WATER }.sumOf { it.cups }
                        toast.value = if (total > 0) "$BUDDY_NAME added ${Units.ml(total)} for you." else "$BUDDY_NAME added a log for you."
                    }
                }
        }
    }

    /** His tap on "Log for her": lands in her day once her phone syncs. */
    fun logForHer(kind: LogKind, ml: Int) {
        viewModelScope.launch {
            runCatching { Pairing.addForHer(kind, Units.cups(ml)) }
                .onSuccess { toast.value = if (kind == LogKind.WATER) "Added ${ml} ml for $HER_NAME." else "Added a ${kind.name.lowercase()} for $HER_NAME." }
                .onFailure { toast.value = "Couldn't add it: ${it.reason()}." }
        }
    }

    fun setAllowAdds(allow: Boolean) {
        viewModelScope.launch { runCatching { Pairing.setAllowAdds(allow) } }
    }

    /**
     * Every time the app opens (and after the role or her answer changes), make sure this phone holds its
     * seat: his always, hers once she has said yes. Retries with growing gaps; a seat held by another
     * install stops the retries and says so.
     */
    /** Checks GitHub for a newer release at most once an hour while the app is in use. */
    fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheck < 60 * 60 * 1000L) return
        lastUpdateCheck = now
        viewModelScope.launch { update.value = Updater.check() }
    }

    fun installUpdate() {
        val release = update.value ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            toast.value = "Allow Sip & Snack to install updates, then tap Update again."
            Updater.openInstallPermission(app)
            return
        }
        updating.value = true
        viewModelScope.launch {
            val ok = runCatching { Updater.downloadAndInstall(app, release) }.getOrDefault(false)
            updating.value = false
            if (!ok) toast.value = "The download didn't finish. Check your internet and tap Update again."
        }
    }

    fun autoLink() {
        if (!Pairing.available) return
        val wants = when (role.value) {
            "buddy" -> "buddy"
            "her" -> if (prefs.shareAnswer == "yes") "her" else null
            else -> null
        } ?: run { linkProblem.value = null; return }
        linking?.cancel()
        linking = viewModelScope.launch {
            for (wait in listOf(0L, 3_000L, 10_000L, 30_000L, 60_000L, 120_000L)) {
                delay(wait)
                val result = runCatching { if (wants == "her") Pairing.linkHer() else Pairing.linkBuddy() }
                myUid.value = runCatching { Pairing.uid() }.getOrNull()
                if (result.isSuccess) { linkProblem.value = null; return@launch }
                val error = result.exceptionOrNull()!!
                linkProblem.value = error.reason()
            }
        }
    }

    /** On her phone, any change to what she shares reaches her buddy a moment later. */
    @OptIn(FlowPreview::class)
    private fun syncHerDay() {
        viewModelScope.launch {
            merge(todayLogs, pair, goal, myUid, steps, stepGoal, shareSteps, meds, shareMeds)
                .debounce(1500)
                .collect {
                    val p = pair.value
                    val me = myUid.value
                    if (role.value != "her" || p == null || me == null || p.ownerUid != me || !p.sharing || p.paused) return@collect
                    val day = HerDay.build(todayLogs.value.map { it.toEntry() }, prefs, p.level, steps.value, meds.value)
                    runCatching { Pairing.pushDay(LocalDate.now().toString(), day) }
                    if (day.steps != null && !prefs.toldStepsShared) {
                        prefs.toldStepsShared = true
                        toast.value = "New: $BUDDY_NAME can see your steps too. Change it in Sharing."
                    }
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
        if (newRole == "her") {
            askShare.value = Pairing.available && prefs.shouldAskToShare()
            seedMeds()
        }
        autoLink()
    }

    /** For a phone that picked the wrong role: frees its seat and asks again. */
    fun switchRole() {
        linking?.cancel()
        linkProblem.value = null
        viewModelScope.launch {
            runCatching { Pairing.releaseMySeat() }
            prefs.role = null
            role.value = null
        }
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

    /** Her explicit "share" tap: links her seat and un-pauses. */
    fun startSharing() {
        prefs.shareAnswer = "yes"
        viewModelScope.launch {
            runCatching {
                Pairing.linkHer()
                Pairing.setSharing(true, false, pair.value?.level ?: ShareLevel.WITH_MEALS)
            }.onSuccess {
                linkProblem.value = null
            }.onFailure {
                linkProblem.value = it.reason()
                toast.value = "Couldn't turn on sharing: ${it.reason()}."
                autoLink()
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
        celebrate(reached, cups)
    }

    private fun celebrate(reached: Moment, cups: Double) {
        if (!memesOn.value) {
            toast.value = if (reached == Moment.GOAL_HIT) "Goal hit. Proud of you!" else "Logged."
            return
        }
        if (reached == Moment.CUP_LOGGED) {
            Memes.forMoment(reached).sound?.let { Memes.playSound(getApplication(), it) }
            toast.value = "Let's have it! +${com.sipsnack.buddy.rules.Units.ml(cups)}"
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
