package com.ainotes.studyassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadedFileDao {
    @Query("SELECT * FROM uploaded_files ORDER BY uploadedAt DESC")
    fun observeAll(): Flow<List<UploadedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: UploadedFileEntity): Long
}
