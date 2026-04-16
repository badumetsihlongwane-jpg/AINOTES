package com.ainotes.studyassistant.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import com.ainotes.studyassistant.data.local.entity.ReminderEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity
import com.ainotes.studyassistant.domain.StudyRepository
import com.ainotes.studyassistant.notifications.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class WorkspaceUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val files: List<UploadedFileEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val progressLogs: List<ProgressLogEntity> = emptyList(),
    val averageProgress: Int = 0,
    val openTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val upcomingTasks: List<TaskEntity> = emptyList(),
    val upcomingReminders: List<ReminderEntity> = emptyList()
)

class WorkspaceViewModel(
    private val repository: StudyRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val uiState: StateFlow<WorkspaceUiState> = combine(
        repository.subjects,
        repository.tasks,
        repository.notes,
        repository.files,
        repository.reminders,
        repository.progressLogs
    ) { subjects, tasks, notes, files, reminders, logs ->
        val now = System.currentTimeMillis()
        val averageProgress = if (tasks.isEmpty()) 0 else tasks.map { it.progressPercent }.average().roundToInt()
        WorkspaceUiState(
            subjects = subjects,
            tasks = tasks,
            notes = notes,
            files = files,
            reminders = reminders,
            progressLogs = logs,
            averageProgress = averageProgress,
            openTaskCount = tasks.count { !it.isCompleted },
            completedTaskCount = tasks.count { it.isCompleted },
            upcomingTasks = tasks
                .filter { it.dueAt != null && (it.dueAt ?: 0) >= now && !it.isCompleted }
                .sortedBy { it.dueAt }
                .take(5),
            upcomingReminders = reminders
                .filter { it.triggerAt >= now }
                .sortedBy { it.triggerAt }
                .take(5)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkspaceUiState()
    )

    fun addSubject(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addSubject(name, description, "#3F6B49")
        }
    }

    fun addTask(title: String, subjectId: Long?) {
        if (title.isBlank()) return
        val dueAt = System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L
        viewModelScope.launch {
            repository.addTask(
                title = title,
                description = "Quick task from workspace",
                subjectId = subjectId,
                dueAt = dueAt,
                priority = 2
            )
        }
    }

    fun addNote(title: String, content: String, subjectId: Long?) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.addNote(subjectId, title, content)
        }
    }

    fun addReminder(
        title: String,
        message: String,
        delayMinutes: Long,
        subjectId: Long?,
        taskId: Long?
    ) {
        if (title.isBlank()) return
        val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L
        viewModelScope.launch {
            val reminderId = repository.addReminder(title, message.ifBlank { "Study reminder" }, triggerAt, taskId, subjectId)
            reminderScheduler.schedule(reminderId, title, message.ifBlank { "Study reminder" }, triggerAt)
        }
    }

    fun addUploadedContext(
        name: String,
        uri: String,
        mimeType: String,
        sizeBytes: Long?,
        subjectId: Long?,
        taskId: Long?,
        intentText: String
    ) {
        if (name.isBlank() || uri.isBlank()) return
        viewModelScope.launch {
            repository.addFile(
                name = name,
                uri = uri,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                subjectId = subjectId,
                taskId = taskId
            )
            if (intentText.isNotBlank()) {
                repository.addNote(
                    subjectId = subjectId,
                    title = "Context intent: $name",
                    content = intentText
                )
            }
        }
    }

    fun nudgeTaskProgress(taskId: Long, delta: Int = 15) {
        val task = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        val updated = (task.progressPercent + delta).coerceIn(0, 100)
        viewModelScope.launch {
            repository.updateTaskProgress(taskId, updated, "Quick update from workspace")
        }
    }
}
