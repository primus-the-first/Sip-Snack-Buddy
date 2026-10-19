package com.sipsnack.buddy.sync

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.sipsnack.buddy.PAIR_ID
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.rules.ShareLevel
import com.sipsnack.buddy.rules.SharedDay
import com.sipsnack.buddy.rules.SharedEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** The shared pair as either phone sees it. */
data class PairState(
    val ownerUid: String?,
    val buddyUid: String?,
    val sharing: Boolean,
    val paused: Boolean,
    val level: ShareLevel,
    val cheer: String?,
    val cheerAt: Long,
    /** Whether she lets her buddy add logs to her day. On unless she turns it off. */
    val allowAdds: Boolean,
)

/** A log her buddy added for her, waiting for her phone to take it into her day. */
data class AddedLog(val id: String, val kind: LogKind, val cups: Double, val atMillis: Long)

data class DayDoc(val date: String, val day: SharedDay, val updatedAt: Long)

/** "taken": the seat belongs to another install. */
class PairingException(message: String) : Exception(message)

/** Plain words for a failed Firestore call, shown so problems can be diagnosed from a screenshot. */
fun Throwable.reason(): String = when (this) {
    is PairingException -> "the database refused the seat (it may belong to an older install of the app)"
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE -> "no internet connection"
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "the database refused it (permission denied)"
        else -> code.name.lowercase().replace('_', ' ')
    }
    else -> message ?: javaClass.simpleName
}

/** Talks to Firestore for live sharing. Everything here is a no-op when Firebase isn't configured. */
object Pairing {
    val available: Boolean get() = runCatching { FirebaseApp.getInstance() }.isSuccess

    private val db get() = FirebaseFirestore.getInstance()
    private val pair get() = db.collection("pairs").document(PAIR_ID)
    private val days get() = pair.collection("days")
    private val added get() = pair.collection("added")

    private val signIn = Mutex()

    /**
     * Anonymous sign-in: no accounts, just a stable id per install. Only one sign-in may run at a time:
     * several at once each create an account, and the phone ends up writing as one id while claiming
     * its seat as another, which the rules (rightly) refuse.
     */
    suspend fun uid(): String = signIn.withLock {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user!!
        // Have the sign-in pass in hand before any write, so the first write can't go out without it.
        user.getIdToken(false).await()
        user.uid
    }

    /**
     * Her phone takes the owner seat (or confirms it has it) with sharing on. Leaves pause and level as
     * they are, so it is safe to call every time her app opens.
     */
    suspend fun linkHer() {
        val me = uid()
        claim(
            update = mapOf("ownerUid" to me, "sharing" to true, "updatedAt" to System.currentTimeMillis()),
            create = mapOf(
                "ownerUid" to me, "buddyUid" to null, "sharing" to true, "paused" to false,
                "level" to ShareLevel.WITH_MEALS.name, "cheer" to null, "cheerAt" to 0L, "updatedAt" to System.currentTimeMillis(),
            ),
        )
    }

    /** His phone takes the buddy seat, or confirms it has it. Safe to call every time his app opens. */
    suspend fun linkBuddy() {
        val me = uid()
        claim(
            update = mapOf("buddyUid" to me),
            create = mapOf(
                "ownerUid" to null, "buddyUid" to me, "sharing" to false, "paused" to false,
                "level" to ShareLevel.WITH_MEALS.name, "cheer" to null, "cheerAt" to 0L, "updatedAt" to 0L,
            ),
        )
    }

    /**
     * Takes a seat in the existing pair, or creates the pair if there is none. The rules refuse an update
     * to a missing document with PERMISSION_DENIED (not NOT_FOUND), so a refusal is answered with a create.
     * If the pair does exist, the create is refused too (it would overwrite the other seat): then the seat
     * really is someone else's.
     */
    private suspend fun claim(update: Map<String, Any?>, create: Map<String, Any?>) {
        try {
            pair.update(update).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND && e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            try {
                pair.set(create).await()
            } catch (e2: FirebaseFirestoreException) {
                throw denied(e2)
            }
        }
    }

    /** A refused seat claim means the seat is someone else's; anything else is passed on as it is. */
    private fun denied(e: FirebaseFirestoreException): Exception =
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PairingException("taken") else e

    suspend fun setSharing(sharing: Boolean, paused: Boolean, level: ShareLevel) {
        pair.update(mapOf("sharing" to sharing, "paused" to paused, "level" to level.name, "updatedAt" to System.currentTimeMillis())).await()
    }

    /** Stops sharing and deletes every shared day. Her logs on her phone stay. */
    suspend fun stopAndDelete() {
        val docs = days.get().await().documents
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        setSharing(sharing = false, paused = false, level = ShareLevel.WITH_MEALS)
    }

    /** Frees whichever seat this phone holds, checked against the database, for a phone that picked the wrong role. */
    suspend fun releaseMySeat() {
        val me = uid()
        val current = runCatching { pair.get().await() }.getOrNull() ?: return
        if (current.getString("ownerUid") == me) pair.update("ownerUid", null).await()
        if (current.getString("buddyUid") == me) pair.update("buddyUid", null).await()
    }

    suspend fun pushDay(date: String, day: SharedDay) {
        days.document(date).set(
            mapOf(
                "cups" to day.cups,
                "goal" to day.goal,
                "lastSipAt" to day.lastSipMillis,
                "events" to day.events.map { mapOf("t" to it.atMillis, "k" to it.kind.name, "c" to it.cups) },
                "steps" to day.steps,
                "stepGoal" to day.stepGoal,
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    /** His phone adds a log to her day. Her phone takes it in (and deletes it here) when it next syncs. */
    suspend fun addForHer(kind: LogKind, cups: Double) {
        uid()
        added.add(mapOf("kind" to kind.name, "cups" to cups, "atMillis" to System.currentTimeMillis())).await()
    }

    suspend fun readAdded(): List<AddedLog> = added.get().await().documents.mapNotNull { it.toAdded() }

    suspend fun deleteAdded(id: String) {
        added.document(id).delete().await()
    }

    /** Logs waiting in the shared pair: on his phone, the ones not yet taken in; on hers, the ones to take in. */
    fun observeAdded(): Flow<List<AddedLog>> {
        if (!available) return flowOf(emptyList())
        return callbackFlow {
            uid()
            val reg = added.addSnapshotListener { snap, err ->
                if (err != null) close(err) else trySend(snap?.documents?.mapNotNull { it.toAdded() }.orEmpty())
            }
            awaitClose { reg.remove() }
        }.retryWhen { _, attempt ->
            emit(emptyList())
            delay(minOf(30_000L, 3_000L * (attempt + 1)))
            true
        }
    }

    suspend fun setAllowAdds(allow: Boolean) {
        pair.update(mapOf("allowAdds" to allow, "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun sendCheer(text: String) {
        pair.update(mapOf("cheer" to text, "cheerAt" to System.currentTimeMillis())).await()
    }

    suspend fun readPair(): PairState? = pair.get().await().toPair()

    suspend fun readDay(date: String): DayDoc? = days.document(date).get().await().toDay()

    /**
     * Live pair state. A Firestore listener dies on its first error, and the first read is refused until
     * this phone holds a seat, so the listener restarts a few seconds after any error.
     */
    fun observePair(): Flow<PairState?> {
        if (!available) return flowOf(null)
        return callbackFlow {
            uid()
            val reg = pair.addSnapshotListener { snap, err ->
                if (err != null) close(err) else trySend(snap?.toPair())
            }
            awaitClose { reg.remove() }
        }.retryWhen { _, attempt ->
            emit(null)
            delay(minOf(30_000L, 3_000L * (attempt + 1)))
            true
        }
    }

    /**
     * Shared days from [fromDate] (yyyy-mm-dd) on, newest first. Asked as an ascending range on the id, which
     * Firestore's built-in index serves. Descending by id (and limitToLast, which the SDK sends as descending)
     * needs a custom index, and without it his view stayed empty. Retries after errors, such as while she is
     * paused or a seat is still linking.
     */
    fun observeWeek(fromDate: String): Flow<List<DayDoc>> {
        if (!available) return flowOf(emptyList())
        return callbackFlow {
            uid()
            val reg = days.whereGreaterThanOrEqualTo(FieldPath.documentId(), fromDate).orderBy(FieldPath.documentId())
                .addSnapshotListener { snap, err ->
                    if (err != null) close(err) else trySend(snap?.documents.orEmpty().mapNotNull { it.toDay() }.reversed())
                }
            awaitClose { reg.remove() }
        }.retryWhen { _, attempt ->
            emit(emptyList())
            delay(minOf(60_000L, 5_000L * (attempt + 1)))
            true
        }
    }

    private fun DocumentSnapshot.toPair(): PairState? {
        if (!exists()) return null
        return PairState(
            ownerUid = getString("ownerUid"),
            buddyUid = getString("buddyUid"),
            sharing = getBoolean("sharing") ?: false,
            paused = getBoolean("paused") ?: false,
            level = runCatching { ShareLevel.valueOf(getString("level") ?: "") }.getOrDefault(ShareLevel.WITH_MEALS),
            cheer = getString("cheer"),
            cheerAt = getLong("cheerAt") ?: 0L,
            allowAdds = getBoolean("allowAdds") ?: true,
        )
    }

    private fun DocumentSnapshot.toAdded(): AddedLog? {
        val kind = runCatching { LogKind.valueOf(getString("kind") ?: "") }.getOrNull() ?: return null
        return AddedLog(id, kind, getDouble("cups") ?: 0.0, getLong("atMillis") ?: return null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toDay(): DayDoc? {
        if (!exists()) return null
        val events = (get("events") as? List<Map<String, Any?>>).orEmpty().mapNotNull { e ->
            val kind = runCatching { LogKind.valueOf(e["k"] as String) }.getOrNull() ?: return@mapNotNull null
            SharedEvent((e["t"] as Number).toLong(), kind, (e["c"] as Number).toDouble())
        }
        return DayDoc(
            date = id,
            day = SharedDay(
                cups = getDouble("cups") ?: 0.0,
                goal = getLong("goal")?.toInt() ?: 8,
                lastSipMillis = getLong("lastSipAt"),
                events = events,
                steps = getLong("steps")?.toInt(),
                stepGoal = getLong("stepGoal")?.toInt(),
            ),
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }
}
