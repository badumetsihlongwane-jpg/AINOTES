package com.ainotes.studyassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "uploaded_files",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("taskId")]
)
data class UploadedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val uri: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long? = null,
    val subjectId: Long? = null,
    val taskId: Long? = null,
    val uploadedAt: Long = System.currentTimeMillis()
)
