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
