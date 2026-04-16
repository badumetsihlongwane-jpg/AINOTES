package com.ainotes.studyassistant.domain

import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import com.ainotes.studyassistant.data.local.entity.ReminderEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity
import kotlinx.coroutines.flow.Flow

data class DashboardSnapshot(
    val subjectCount: Int,
    val openTaskCount: Int,
    val noteCount: Int,
    val fileCount: Int,
    val upcomingTasks: List<TaskEntity>
)

interface StudyRepository {
    val subjects: Flow<List<SubjectEntity>>
    val tasks: Flow<List<TaskEntity>>
    val notes: Flow<List<NoteEntity>>
    val files: Flow<List<UploadedFileEntity>>
    val reminders: Flow<List<ReminderEntity>>
    val progressLogs: Flow<List<ProgressLogEntity>>
    val dashboard: Flow<DashboardSnapshot>

    suspend fun addSubject(name: String, description: String, colorHex: String)
    suspend fun addTask(
        title: String,
        description: String,
        subjectId: Long?,
        dueAt: Long?,
        priority: Int
    ): Long

    suspend fun addNote(subjectId: Long?, title: String, content: String)
    suspend fun addFile(
        name: String,
        uri: String,
        mimeType: String,
        sizeBytes: Long?,
        subjectId: Long?,
        taskId: Long?
    )

    suspend fun addReminder(
        title: String,
        message: String,
        triggerAt: Long,
        taskId: Long?,
        subjectId: Long?
    ): Long

    suspend fun updateTaskProgress(taskId: Long, progressPercent: Int, note: String)
}
