package com.ainotes.studyassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY COALESCE(dueAt, 9223372036854775807) ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueAt IS NOT NULL AND dueAt >= :fromEpoch ORDER BY dueAt ASC LIMIT :limit")
    fun observeUpcoming(fromEpoch: Long, limit: Int = 10): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET progressPercent = :progress, isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun updateProgress(taskId: Long, progress: Int, isCompleted: Boolean)

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: Long): TaskEntity?
}
