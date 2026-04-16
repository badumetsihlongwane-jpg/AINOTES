package com.ainotes.studyassistant.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.ai.AiProgressPlan
import com.ainotes.studyassistant.ai.AiStudyPlan
import com.ainotes.studyassistant.ai.StudyAiEngine
import com.ainotes.studyassistant.ai.StudyAiInput
import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import com.ainotes.studyassistant.data.local.entity.ReminderEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity
import com.ainotes.studyassistant.domain.StudyRepository
import com.ainotes.studyassistant.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val reminderScheduler: ReminderScheduler,
    private val aiEngine: StudyAiEngine
) : ViewModel() {

    private data class WorkspaceBaseState(
        val subjects: List<SubjectEntity>,
        val tasks: List<TaskEntity>,
        val notes: List<NoteEntity>,
        val files: List<UploadedFileEntity>,
        val reminders: List<ReminderEntity>
    )

    private data class AppliedPlanStats(
        val subjects: Int = 0,
        val tasks: Int = 0,
        val reminders: Int = 0,
        val notes: Int = 0,
        val progressUpdates: Int = 0
    )

    private val _assistantFeed = MutableStateFlow(
        if (aiEngine.isConfigured) {
            "Autopilot is ready. Upload a semester plan to let AI organize your workspace."
        } else {
            "Autopilot is off. Configure GEMINI_API_KEY to enable AI planning."
        }
    )
    val assistantFeed: StateFlow<String> = _assistantFeed

    val isAiReady: Boolean
        get() = aiEngine.isConfigured

    val uiState: StateFlow<WorkspaceUiState> = combine(
        repository.subjects,
        repository.tasks,
        repository.notes,
        repository.files,
        repository.reminders
    ) { subjects, tasks, notes, files, reminders ->
        WorkspaceBaseState(
            subjects = subjects,
            tasks = tasks,
            notes = notes,
            files = files,
            reminders = reminders
        )
    }.combine(repository.progressLogs) { base, logs ->
        val now = System.currentTimeMillis()
        val averageProgress = if (base.tasks.isEmpty()) 0 else {
            base.tasks.map { it.progressPercent }.average().roundToInt()
        }
        WorkspaceUiState(
            subjects = base.subjects,
            tasks = base.tasks,
            notes = base.notes,
            files = base.files,
            reminders = base.reminders,
            progressLogs = logs,
            averageProgress = averageProgress,
            openTaskCount = base.tasks.count { !it.isCompleted },
            completedTaskCount = base.tasks.count { it.isCompleted },
            upcomingTasks = base.tasks
                .filter { it.dueAt != null && (it.dueAt ?: 0) >= now && !it.isCompleted }
                .sortedBy { it.dueAt }
                .take(5),
            upcomingReminders = base.reminders
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
        intentText: String,
        useAiAutoplan: Boolean
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

            if (!useAiAutoplan) {
                _assistantFeed.value = "Context stored. Manual mode kept as requested."
                return@launch
            }

            val snapshot = uiState.value
            val aiResult = aiEngine.generatePlan(
                StudyAiInput(
                    fileName = name,
                    mimeType = mimeType,
                    intentText = intentText,
                    existingSubjects = snapshot.subjects.map { it.name },
                    openTasks = snapshot.tasks.filter { !it.isCompleted }.map { it.title },
                    upcomingDeadlines = snapshot.upcomingTasks.mapNotNull { task ->
                        task.dueAt?.let { dueAt -> "${task.title} | $dueAt" }
                    }
                )
            )

            val appliedStats = applyAiPlan(aiResult.plan, snapshot)
            val report = buildString {
                appendLine(aiResult.summary)
                appendLine()
                appendLine("AI actions applied:")
                appendLine("Subjects +${appliedStats.subjects}")
                appendLine("Tasks +${appliedStats.tasks}")
                appendLine("Reminders +${appliedStats.reminders}")
                appendLine("Notes +${appliedStats.notes}")
                appendLine("Progress updates +${appliedStats.progressUpdates}")
                if (aiResult.isFallback) {
                    appendLine()
                    append("Fallback mode: AI response was unavailable or invalid.")
                }
            }.trim()

            repository.addNote(
                subjectId = subjectId,
                title = "AI Report: $name",
                content = report
            )
            _assistantFeed.value = report
        }
    }

    fun nudgeTaskProgress(taskId: Long, delta: Int = 15) {
        val task = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        val updated = (task.progressPercent + delta).coerceIn(0, 100)
        viewModelScope.launch {
            repository.updateTaskProgress(taskId, updated, "Quick update from workspace")
        }
    }

    private suspend fun applyAiPlan(
        plan: AiStudyPlan,
        snapshot: WorkspaceUiState
    ): AppliedPlanStats {
        val now = System.currentTimeMillis()
        val subjectIndex = snapshot.subjects
            .associate { normalizeKey(it.name) to it.id }
            .toMutableMap()
        val taskIndex = snapshot.tasks
            .associate { normalizeKey(it.title) to it.id }
            .toMutableMap()

        var subjectCount = 0
        var taskCount = 0
        var reminderCount = 0
        var noteCount = 0
        var progressCount = 0

        for (subject in plan.subjects) {
            val key = normalizeKey(subject.name)
            if (key.isBlank() || subjectIndex.containsKey(key)) continue
            val newId = repository.addSubject(
                name = subject.name,
                description = subject.description,
                colorHex = "#3F6B49"
            )
            subjectIndex[key] = newId
            subjectCount += 1
        }

        for (task in plan.tasks) {
            val titleKey = normalizeKey(task.title)
            if (titleKey.isBlank()) continue
            val subjectId = ensureSubjectId(task.subjectName, subjectIndex)
            val dueAt = task.dueAtEpochMillis ?: (now + 5L * 24L * 60L * 60L * 1000L)
            val taskId = repository.addTask(
                title = task.title,
                description = task.description,
                subjectId = subjectId,
                dueAt = dueAt,
                priority = task.priority
            )
            taskIndex[titleKey] = taskId
            taskCount += 1

            val baselineProgress = task.progressPercent
            if (baselineProgress != null && baselineProgress > 0) {
                repository.updateTaskProgress(
                    taskId = taskId,
                    progressPercent = baselineProgress,
                    note = "AI baseline progress"
                )
                progressCount += 1
            }
        }

        for (quiz in plan.quizzes) {
            val subjectId = ensureSubjectId(quiz.subjectName, subjectIndex)
            val scheduledAt = quiz.scheduledAtEpochMillis ?: (now + 7L * 24L * 60L * 60L * 1000L)
            val quizTaskId = repository.addTask(
                title = "Quiz: ${quiz.title}",
                description = quiz.description.ifBlank { "AI-generated self test" },
                subjectId = subjectId,
                dueAt = scheduledAt,
                priority = 3
            )
            taskIndex[normalizeKey("Quiz: ${quiz.title}")] = quizTaskId
            taskCount += 1

            val reminderAt = scheduledAt.coerceAtLeast(now + 60_000L)
            val reminderTitle = "Quiz alert: ${quiz.title}"
            val reminderId = repository.addReminder(
                title = reminderTitle,
                message = "AI prepared this quiz checkpoint for revision.",
                triggerAt = reminderAt,
                taskId = quizTaskId,
                subjectId = subjectId
            )
            reminderScheduler.schedule(
                reminderId = reminderId,
                title = reminderTitle,
                message = "AI prepared this quiz checkpoint for revision.",
                triggerAt = reminderAt
            )
            reminderCount += 1
        }

        for (note in plan.notes) {
            val subjectId = ensureSubjectId(note.subjectName, subjectIndex)
            repository.addNote(
                subjectId = subjectId,
                title = note.title,
                content = note.content
            )
            noteCount += 1
        }

        for (reminder in plan.reminders) {
            val subjectId = ensureSubjectId(reminder.subjectName, subjectIndex)
            val taskId = reminder.taskTitle
                ?.let { taskIndex[normalizeKey(it)] }
            val triggerAt = (reminder.triggerAtEpochMillis ?: (now + 24L * 60L * 60L * 1000L))
                .coerceAtLeast(now + 60_000L)
            val reminderId = repository.addReminder(
                title = reminder.title,
                message = reminder.message.ifBlank { "AI reminder" },
                triggerAt = triggerAt,
                taskId = taskId,
                subjectId = subjectId
            )
            reminderScheduler.schedule(
                reminderId = reminderId,
                title = reminder.title,
                message = reminder.message.ifBlank { "AI reminder" },
                triggerAt = triggerAt
            )
            reminderCount += 1
        }

        for (progress in plan.progressUpdates) {
            applyProgressUpdate(progress, taskIndex)?.let {
                progressCount += 1
            }
        }

        return AppliedPlanStats(
            subjects = subjectCount,
            tasks = taskCount,
            reminders = reminderCount,
            notes = noteCount,
            progressUpdates = progressCount
        )
    }

    private suspend fun applyProgressUpdate(
        progress: AiProgressPlan,
        taskIndex: Map<String, Long>
    ): Unit? {
        val taskId = taskIndex[normalizeKey(progress.taskTitle)] ?: return null
        repository.updateTaskProgress(
            taskId = taskId,
            progressPercent = progress.progressPercent,
            note = progress.note.ifBlank { "AI progress update" }
        )
        return Unit
    }

    private suspend fun ensureSubjectId(
        subjectName: String?,
        subjectIndex: MutableMap<String, Long>
    ): Long? {
        val key = normalizeKey(subjectName.orEmpty())
        if (key.isBlank()) return null

        val existing = subjectIndex[key]
        if (existing != null) return existing

        val created = repository.addSubject(
            name = subjectName.orEmpty(),
            description = "Created by AI from uploaded context",
            colorHex = "#3F6B49"
        )
        subjectIndex[key] = created
        return created
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase()
    }
}
