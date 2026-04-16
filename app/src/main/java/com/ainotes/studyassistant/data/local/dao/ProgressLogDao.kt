package com.ainotes.studyassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressLogDao {
    @Query("SELECT * FROM progress_logs ORDER BY loggedAt DESC")
    fun observeAll(): Flow<List<ProgressLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ProgressLogEntity): Long
}
