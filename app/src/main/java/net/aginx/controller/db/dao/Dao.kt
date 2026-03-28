package net.aginx.controller.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.aginx.controller.db.entities.*

@Dao
interface AginxDao {
    @Query("SELECT * FROM aginx ORDER BY isFavorite DESC, name ASC")
    fun getAll(): Flow<List<AginxEntity>>

    @Query("SELECT * FROM aginx WHERE id = :id")
    suspend fun getById(id: String): AginxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(aginx: AginxEntity)

    @Update
    suspend fun update(aginx: AginxEntity)

    @Delete
    suspend fun delete(aginx: AginxEntity)

    @Query("DELETE FROM aginx WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents WHERE aginxId = :aginxId")
    fun getByAginx(aginxId: String): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id AND aginxId = :aginxId")
    suspend fun getById(id: String, aginxId: String): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(agent: AgentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agents: List<AgentEntity>)

    @Query("UPDATE agents SET workdir = :workdir WHERE id = :id AND aginxId = :aginxId")
    suspend fun updateWorkdir(id: String, aginxId: String, workdir: String?)

    @Query("DELETE FROM agents WHERE aginxId = :aginxId")
    suspend fun deleteByAginx(aginxId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE aginxId = :aginxId AND agentId = :agentId ORDER BY updatedAt DESC")
    fun getByAgent(aginxId: String, agentId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id AND aginxId = :aginxId")
    suspend fun getById(id: String, aginxId: String): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations WHERE aginxId = :aginxId AND agentId = :agentId")
    suspend fun countByAgent(aginxId: String, agentId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id AND aginxId = :aginxId")
    suspend fun deleteById(id: String, aginxId: String)

    @Query("UPDATE conversations SET lastMessage = :message, lastMessageTime = :time, updatedAt = :time WHERE id = :id AND aginxId = :aginxId")
    suspend fun updateLastMessage(id: String, aginxId: String, message: String, time: Long)

    @Query("UPDATE conversations SET sessionId = :sessionId WHERE id = :id AND aginxId = :aginxId")
    suspend fun updateSessionId(id: String, aginxId: String, sessionId: String)

    @Query("UPDATE conversations SET workdir = :workdir WHERE id = :id AND aginxId = :aginxId")
    suspend fun updateWorkdir(id: String, aginxId: String, workdir: String?)
}
