package net.aginx.controller.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.aginx.controller.db.entities.AginxEntity

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
