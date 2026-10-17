package com.sipsnack.buddy.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sipsnack.buddy.rules.LogEntry
import com.sipsnack.buddy.rules.LogKind
import kotlinx.coroutines.flow.Flow

/** One thing she logged. Photos are never stored: the camera only confirms what it was. */
@Entity(tableName = "logs")
data class LogRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val kind: String,
    val cups: Double,
    val label: String,
) {
    fun toEntry() = LogEntry(atMillis, LogKind.valueOf(kind), cups, label)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs WHERE atMillis >= :from AND atMillis < :until ORDER BY atMillis DESC")
    fun between(from: Long, until: Long): Flow<List<LogRow>>

    @Query("SELECT * FROM logs WHERE atMillis >= :from AND atMillis < :until ORDER BY atMillis DESC")
    suspend fun betweenNow(from: Long, until: Long): List<LogRow>

    @Insert
    suspend fun insert(row: LogRow): Long

    @Query("DELETE FROM logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}

@Database(entities = [LogRow::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun logs(): LogDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "sip-snack.db")
                .build().also { instance = it }
        }
    }
}
