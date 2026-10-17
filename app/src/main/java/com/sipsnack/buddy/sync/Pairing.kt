package com.sipsnack.buddy.sync

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
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
)

data class DayDoc(val date: String, val day: SharedDay, val updatedAt: Long)

class PairingException(message: String) : Exception(message)

/** Talks to Firestore for live sharing. Everything here is a no-op when Firebase isn't configured. */
object Pairing {
    val available: Boolean get() = runCatching { FirebaseApp.getInstance() }.isSuccess

    private val db get() = FirebaseFirestore.getInstance()
    private val pair get() = db.collection("pairs").document(PAIR_ID)
    private val days get() = pair.collection("days")

    /** Anonymous sign-in: no accounts, just a stable id per install. */
    suspend fun uid(): String {
        val auth = FirebaseAuth.getInstance()
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    }

    /** Her phone takes the owner seat and turns sharing on. */
    suspend fun claimHer(level: ShareLevel) {
        val me = uid()
        val fields = mapOf(
            "ownerUid" to me, "sharing" to true, "paused" to false,
            "level" to level.name, "updatedAt" to System.currentTimeMillis(),
        )
        try {
            pair.update(fields).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw takenOr(e, "owner")
            pair.set(fields + mapOf("buddyUid" to null, "cheer" to null, "cheerAt" to 0L)).await()
        }
    }

    /** His phone takes the buddy seat. */
    suspend fun claimBuddy() {
        val me = uid()
        try {
            pair.update("buddyUid", me).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw takenOr(e, "buddy")
            pair.set(
                mapOf(
                    "ownerUid" to null, "buddyUid" to me, "sharing" to false, "paused" to false,
                    "level" to ShareLevel.WITH_MEALS.name, "cheer" to null, "cheerAt" to 0L, "updatedAt" to 0L,
                ),
            ).await()
        }
    }

    private suspend fun takenOr(e: FirebaseFirestoreException, seat: String): Exception {
        if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return e
        // Denied either because the seat is someone else's, or because it's already ours.
        val mine = runCatching { pair.get().await() }.getOrNull()?.let {
            it.getString(if (seat == "owner") "ownerUid" else "buddyUid") == uid()
        } ?: false
        return if (mine) PairingException("already") else PairingException("taken")
    }

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

    /** Frees her seat, for a phone that picked the wrong role. */
    suspend fun releaseOwner() {
        pair.update("ownerUid", null).await()
    }

    suspend fun releaseBuddy() {
        pair.update("buddyUid", null).await()
    }

    suspend fun pushDay(date: String, day: SharedDay) {
        days.document(date).set(
            mapOf(
                "cups" to day.cups,
                "goal" to day.goal,
                "lastSipAt" to day.lastSipMillis,
                "events" to day.events.map { mapOf("t" to it.atMillis, "k" to it.kind.name, "c" to it.cups) },
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
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

    /** The last 7 shared days, newest first. Fails quietly (empty) when she's paused or not sharing. */
    fun observeWeek(): Flow<List<DayDoc>> {
        if (!available) return flowOf(emptyList())
        return callbackFlow {
            uid()
            val reg = days.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING).limit(7)
                .addSnapshotListener { snap, err ->
                    trySend(if (err != null || snap == null) emptyList() else snap.documents.mapNotNull { it.toDay() })
                }
            awaitClose { reg.remove() }
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
        )
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
            ),
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }
}
