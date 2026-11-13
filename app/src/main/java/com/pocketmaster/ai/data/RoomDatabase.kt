package com.pocketmaster.ai.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Room database holding chat history:
 *  ChatSession, ChatMessage, ToneSnapshot.
 *  All data survives app restarts.
 */
class RoomDatabase internal constructor(private val context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        PocketMasterDatabase::class.java,
        "pocketmaster_chat.db"
    ).build()

    val chatDao: ChatDao = db.chatDao

    companion object {
        fun create(context: Context): RoomDatabase = RoomDatabase(context)
    }
}

/** Database entry point. */
abstract class PocketMasterDatabase : androidx.room.RoomDatabase() {
    abstract val chatDao: ChatDao

    @TypeConverters(Converters::class)
    companion object {
        private var instance: PocketMasterDatabase? = null

        fun create(context: Context): PocketMasterDatabase {
            if (instance != null) return instance!!
            synchronized(PocketMasterDatabase::class.java) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        PocketMasterDatabase::class.java,
                        "pocketmaster_chat.db"
                    ).build()
                }
            }
            return instance!!
        }
    }
}

/** A chat conversation (one per agent session). */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val providerSnapshot: String
)

/** One message inside a session. */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val toolCalls: String
)

/** Snapshot of the pedalboard right before an agent tone load. */
@Entity(tableName = "tone_snapshots")
data class ToneSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val presetId: Int,
    val blockStates: String,
    val params: String,
    val chain: String,
    val presetVolume: Float,
    val timestamp: Long,
    val label: String
)

/** DAO for chat access. */
@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMessage(message: ChatMessage)
    @Insert suspend fun insertSession(session: ChatSession): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSnapshot(snapshot: ToneSnapshot)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(sessionId: Long): kotlinx.coroutines.flow.Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getSessions(): kotlinx.coroutines.flow.Flow<List<ChatSession>>

    @Query("SELECT * FROM tone_snapshots WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSnapshot(sessionId: Long): kotlinx.coroutines.flow.Flow<ToneSnapshot?>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): ChatSession?

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}

/** Convert JSON arrays/objects to/from stored text. */
@TypeConverters
class Converters {
    @TypeConverter
    fun fromJsonArray(array: String): String = array

    @TypeConverter
    fun toJsonArray(json: String): String = json

    @TypeConverter
    fun fromJsonObject(obj: String): String = obj

    @TypeConverter
    fun toJsonObject(json: String): String = json
}

fun ChatMessage.copy(role: String = this.role, content: String = this.content, toolCalls: String = this.toolCalls): ChatMessage =
    ChatMessage(id = this.id, sessionId = this.sessionId, role = role, content = content, timestamp = this.timestamp, toolCalls = toolCalls)
