package com.ainotes.studyassistant.data.repository

import com.ainotes.studyassistant.data.local.AppDatabase
import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import com.ainotes.studyassistant.data.local.entity.ReminderEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity
import com.ainotes.studyassistant.domain.DashboardSnapshot
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DefaultStudyRepository(
    private val database: AppDatabase
) : StudyRepository {

    override val subjects: Flow<List<SubjectEntity>> = database.subjectDao().observeAll()
    override val tasks: Flow<List<TaskEntity>> = database.taskDao().observeAll()
    override val notes: Flow<List<NoteEntity>> = database.noteDao().observeAll()
    override val files: Flow<List<UploadedFileEntity>> = database.uploadedFileDao().observeAll()
    override val reminders: Flow<List<ReminderEntity>> = database.reminderDao().observeAll()
    override val progressLogs: Flow<List<ProgressLogEntity>> = database.progressLogDao().observeAll()

    override val dashboard: Flow<DashboardSnapshot> = combine(
        subjects,
        tasks,
        notes,
        files,
        database.taskDao().observeUpcoming(System.currentTimeMillis(), 8)
    ) { subjectList, taskList, noteList, fileList, upcoming ->
        DashboardSnapshot(
            subjectCount = subjectList.size,
            openTaskCount = taskList.count { !it.isCompleted },
            noteCount = noteList.size,
            fileCount = fileList.size,
            upcomingTasks = upcoming
        )
    }

    override suspend fun addSubject(name: String, description: String, colorHex: String): Long {
        return database.subjectDao().insert(
            SubjectEntity(
                name = name.trim(),
                description = description.trim(),
                colorHex = colorHex.trim().ifBlank { "#2E7D32" }
            )
        )
    }

    override suspend fun addTask(
        title: String,
        description: String,
        subjectId: Long?,
        dueAt: Long?,
        priority: Int
    ): Long {
        return database.taskDao().insert(
            TaskEntity(
                title = title.trim(),
                description = description.trim(),
                subjectId = subjectId,
                dueAt = dueAt,
                priority = priority.coerceIn(1, 3)
            )
        )
    }

    override suspend fun addNote(subjectId: Long?, title: String, content: String) {
        database.noteDao().insert(
            NoteEntity(
                subjectId = subjectId,
                title = title.trim(),
                content = content.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun addFile(
        name: String,
        uri: String,
        mimeType: String,
        sizeBytes: Long?,
        subjectId: Long?,
        taskId: Long?
    ) {
        database.uploadedFileDao().insert(
            UploadedFileEntity(
                name = name.trim(),
                uri = uri,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                subjectId = subjectId,
                taskId = taskId
            )
        )
    }

    override suspend fun addReminder(
        title: String,
        message: String,
        triggerAt: Long,
        taskId: Long?,
        subjectId: Long?
    ): Long {
        return database.reminderDao().insert(
            ReminderEntity(
                title = title.trim(),
                message = message.trim(),
                triggerAt = triggerAt,
                taskId = taskId,
                subjectId = subjectId
            )
        )
    }

    override suspend fun updateTaskProgress(taskId: Long, progressPercent: Int, note: String) {
        val progress = progressPercent.coerceIn(0, 100)
        val isCompleted = progress == 100
        database.taskDao().updateProgress(taskId, progress, isCompleted)
        database.progressLogDao().insert(
            ProgressLogEntity(
                taskId = taskId,
                progressPercent = progress,
                note = note.trim(),
                loggedAt = System.currentTimeMillis()
            )
        )
    }
}
