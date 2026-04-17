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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            "AI agent ready. It will monitor your study workspace and report back automatically."
        } else {
            "Agent is in rule-only mode. Configure HF_TOKEN (or GEMINI_API_KEY fallback) for AI reasoning."
        }
    )
    val assistantFeed: StateFlow<String> = _assistantFeed

    private val _isAgentWorking = MutableStateFlow(false)
    val isAgentWorking: StateFlow<Boolean> = _isAgentWorking

    val isAiReady: Boolean
        get() = aiEngine.isConfigured

    private var hasInitializedAgentCycle = false
    private var lastAgentRunAt = 0L
    private var lastAiRunAt = 0L

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
                .take(8),
            upcomingReminders = base.reminders
                .filter { it.triggerAt >= now }
                .sortedBy { it.triggerAt }
                .take(8)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkspaceUiState()
    )

    fun onDashboardOpened() {
        if (hasInitializedAgentCycle) return
        hasInitializedAgentCycle = true
        runAgentCheckIn(force = false)
    }

    fun runAgentCheckIn(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_isAgentWorking.value) return
        if (!force && now - lastAgentRunAt < AGENT_COOLDOWN_MILLIS) return

        viewModelScope.launch {
            _isAgentWorking.value = true
            try {
                val snapshot = uiState.value
                val ruleStats = applyRuleBasedInterventions(snapshot)

                var aiStats = AppliedPlanStats()
                var aiSummary = "Rule-based study coach interventions applied."
                val canRunAiReasoning = aiEngine.isConfigured &&
                    (force || now - lastAiRunAt >= AI_REASONING_COOLDOWN_MILLIS)

                if (canRunAiReasoning) {
                    val aiResult = aiEngine.generatePlan(
                        StudyAiInput(
                            fileName = "autonomous-check-in.txt",
                            mimeType = "text/plain",
                            intentText = "Autonomous agent review. Without waiting for user prompting, identify weak performance, upcoming tests/quizzes, and deadlines. Create useful notes, reminders, quiz drills, and progress interventions.",
                            existingSubjects = snapshot.subjects.map { it.name },
                            openTasks = snapshot.tasks.filter { !it.isCompleted }.map { it.title },
                            upcomingDeadlines = snapshot.upcomingTasks.mapNotNull { task ->
                                task.dueAt?.let { dueAt -> "${task.title} | $dueAt" }
                            }
                        )
                    )
                    aiStats = applyAiPlan(aiResult.plan, uiState.value)
                    aiSummary = aiResult.summary
                    lastAiRunAt = System.currentTimeMillis()
                }

                val combinedStats = mergeStats(ruleStats, aiStats)
                val report = buildAgentReport(
                    aiSummary = aiSummary,
                    stats = combinedStats,
                    aiReasoningRan = canRunAiReasoning
                )

                repository.addNote(
                    subjectId = null,
                    title = "AI Agent Report ${reportTimestamp()}",
                    content = report
                )
                _assistantFeed.value = report
                lastAgentRunAt = System.currentTimeMillis()
            } catch (error: Exception) {
                _assistantFeed.value = "Agent check-in failed: ${error.message ?: "Unknown error"}"
            } finally {
                _isAgentWorking.value = false
            }
        }
    }

    fun addSubject(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addSubject(name, description, "#3F6B49")
        }
    }

    fun addTask(title: String, subjectId: Long?) {
        if (title.isBlank()) return
        val dueAt = System.currentTimeMillis() + 3L * DAY
        viewModelScope.launch {
            repository.addTask(
                title = title,
                description = "Quick task from dashboard",
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
        val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * MINUTE
        viewModelScope.launch {
            val reminderId = repository.addReminder(
                title = title,
                message = message.ifBlank { "Study reminder" },
                triggerAt = triggerAt,
                taskId = taskId,
                subjectId = subjectId
            )
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
                _assistantFeed.value = "Context stored. Agent stayed in manual mode as requested."
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
                appendLine("AI actions applied from upload:")
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
            runAgentCheckIn(force = true)
        }
    }

    fun nudgeTaskProgress(taskId: Long, delta: Int = 15) {
        val task = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        val updated = (task.progressPercent + delta).coerceIn(0, 100)
        viewModelScope.launch {
            repository.updateTaskProgress(taskId, updated, "Quick update from dashboard")
        }
    }

    private suspend fun applyRuleBasedInterventions(snapshot: WorkspaceUiState): AppliedPlanStats {
        val now = System.currentTimeMillis()
        val noteKeys = snapshot.notes.map { normalizeKey(it.title) }.toMutableSet()
        val reminderKeys = snapshot.reminders.map { normalizeKey(it.title) }.toMutableSet()
        val taskKeys = snapshot.tasks.map { normalizeKey(it.title) }.toMutableSet()

        var taskCount = 0
        var reminderCount = 0
        var noteCount = 0

        val highRiskTasks = snapshot.tasks
            .filter { task ->
                !task.isCompleted &&
                    task.dueAt != null &&
                    (task.dueAt ?: Long.MAX_VALUE) <= now + 3L * DAY &&
                    task.progressPercent < 60
            }
            .sortedBy { it.dueAt }

        for (task in highRiskTasks.take(3)) {
            val noteTitle = "AI Recovery Plan: ${task.title}"
            if (noteKeys.add(normalizeKey(noteTitle))) {
                val dueDate = task.dueAt?.let { epoch ->
                    Instant.ofEpochMilli(epoch)
                        .atZone(ZoneId.systemDefault())
                        .format(HUMAN_DATE)
                } ?: "soon"
                val content = buildString {
                    appendLine("Task at risk: ${task.title}")
                    appendLine("Due: $dueDate")
                    appendLine("Current progress: ${task.progressPercent}%")
                    appendLine()
                    appendLine("Suggested sprint:")
                    appendLine("1. 25 min revision block")
                    appendLine("2. 10 min self-test")
                    appendLine("3. Update progress and notes")
                }
                repository.addNote(task.subjectId, noteTitle, content.trim())
                noteCount += 1
            }

            val reminderTitle = "AI Check-in: ${task.title}"
            if (reminderKeys.add(normalizeKey(reminderTitle))) {
                val triggerAt = ((task.dueAt ?: now + DAY) - 12L * HOUR)
                    .coerceAtLeast(now + 15L * MINUTE)
                val reminderId = repository.addReminder(
                    title = reminderTitle,
                    message = "Your task needs attention. Quick review sprint now.",
                    triggerAt = triggerAt,
                    taskId = task.id,
                    subjectId = task.subjectId
                )
                reminderScheduler.schedule(
                    reminderId = reminderId,
                    title = reminderTitle,
                    message = "Your task needs attention. Quick review sprint now.",
                    triggerAt = triggerAt
                )
                reminderCount += 1
            }
        }

        val quizTarget = (highRiskTasks + snapshot.tasks.filter { !it.isCompleted && it.progressPercent < 40 })
            .distinctBy { it.id }
            .minByOrNull { it.progressPercent }

        if (quizTarget != null) {
            val quizTitle = "Quiz Drill: ${quizTarget.title}"
            if (taskKeys.add(normalizeKey(quizTitle))) {
                val dueAt = (quizTarget.dueAt ?: now + 2L * DAY).coerceAtLeast(now + 6L * HOUR)
                val quizTaskId = repository.addTask(
                    title = quizTitle,
                    description = "AI generated this drill from weak progress patterns.",
                    subjectId = quizTarget.subjectId,
                    dueAt = dueAt,
                    priority = 3
                )
                taskCount += 1

                val reminderTitle = "Quiz Prep: ${quizTarget.title}"
                if (reminderKeys.add(normalizeKey(reminderTitle))) {
                    val triggerAt = (dueAt - 3L * HOUR).coerceAtLeast(now + 30L * MINUTE)
                    val reminderId = repository.addReminder(
                        title = reminderTitle,
                        message = "AI quiz drill ready. Attempt before deadline.",
                        triggerAt = triggerAt,
                        taskId = quizTaskId,
                        subjectId = quizTarget.subjectId
                    )
                    reminderScheduler.schedule(
                        reminderId = reminderId,
                        title = reminderTitle,
                        message = "AI quiz drill ready. Attempt before deadline.",
                        triggerAt = triggerAt
                    )
                    reminderCount += 1
                }
            }
        }

        return AppliedPlanStats(
            tasks = taskCount,
            reminders = reminderCount,
            notes = noteCount
        )
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
        val noteIndex = snapshot.notes
            .map { normalizeKey(it.title) }
            .toMutableSet()
        val reminderIndex = snapshot.reminders
            .map { normalizeKey(it.title) }
            .toMutableSet()

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

            val existingTaskId = taskIndex[titleKey]
            if (existingTaskId != null) {
                val baselineProgress = task.progressPercent
                if (baselineProgress != null && baselineProgress > 0) {
                    repository.updateTaskProgress(
                        taskId = existingTaskId,
                        progressPercent = baselineProgress,
                        note = "AI refreshed baseline progress"
                    )
                    progressCount += 1
                }
                continue
            }

            val subjectId = ensureSubjectId(task.subjectName, subjectIndex)
            val dueAt = task.dueAtEpochMillis ?: (now + 5L * DAY)
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
            val quizTitle = "Quiz: ${quiz.title}"
            val quizKey = normalizeKey(quizTitle)
            val existingQuizTaskId = taskIndex[quizKey]
            val subjectId = ensureSubjectId(quiz.subjectName, subjectIndex)
            val scheduledAt = quiz.scheduledAtEpochMillis ?: (now + 7L * DAY)

            val quizTaskId = if (existingQuizTaskId != null) {
                existingQuizTaskId
            } else {
                val createdId = repository.addTask(
                    title = quizTitle,
                    description = quiz.description.ifBlank { "AI-generated self test" },
                    subjectId = subjectId,
                    dueAt = scheduledAt,
                    priority = 3
                )
                taskIndex[quizKey] = createdId
                taskCount += 1
                createdId
            }

            val reminderTitle = "Quiz alert: ${quiz.title}"
            val reminderKey = normalizeKey(reminderTitle)
            if (reminderIndex.add(reminderKey)) {
                val reminderAt = scheduledAt.coerceAtLeast(now + MINUTE)
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
        }

        for (note in plan.notes) {
            val titleKey = normalizeKey(note.title)
            if (titleKey.isBlank() || !noteIndex.add(titleKey)) continue
            val subjectId = ensureSubjectId(note.subjectName, subjectIndex)
            repository.addNote(
                subjectId = subjectId,
                title = note.title,
                content = note.content
            )
            noteCount += 1
        }

        for (reminder in plan.reminders) {
            val reminderKey = normalizeKey(reminder.title)
            if (reminderKey.isBlank() || !reminderIndex.add(reminderKey)) continue

            val subjectId = ensureSubjectId(reminder.subjectName, subjectIndex)
            val taskId = reminder.taskTitle
                ?.let { taskIndex[normalizeKey(it)] }
            val triggerAt = (reminder.triggerAtEpochMillis ?: (now + DAY))
                .coerceAtLeast(now + MINUTE)
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
            description = "Created by AI from study context",
            colorHex = "#3F6B49"
        )
        subjectIndex[key] = created
        return created
    }

    private fun mergeStats(first: AppliedPlanStats, second: AppliedPlanStats): AppliedPlanStats {
        return AppliedPlanStats(
            subjects = first.subjects + second.subjects,
            tasks = first.tasks + second.tasks,
            reminders = first.reminders + second.reminders,
            notes = first.notes + second.notes,
            progressUpdates = first.progressUpdates + second.progressUpdates
        )
    }

    private fun buildAgentReport(
        aiSummary: String,
        stats: AppliedPlanStats,
        aiReasoningRan: Boolean
    ): String {
        return buildString {
            appendLine("Agent check-in complete.")
            appendLine(aiSummary)
            appendLine()
            appendLine("Actions this cycle:")
            appendLine("Subjects +${stats.subjects}")
            appendLine("Tasks +${stats.tasks}")
            appendLine("Reminders +${stats.reminders}")
            appendLine("Notes +${stats.notes}")
            appendLine("Progress updates +${stats.progressUpdates}")
            appendLine()
            append(
                if (aiReasoningRan) {
                    "AI reasoning was active for this cycle."
                } else {
                    "Cycle used rule-based coaching only (AI reasoning cooldown or no token)."
                }
            )
        }.trim()
    }

    private fun reportTimestamp(): String {
        return Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(REPORT_TIMESTAMP)
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase()
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        private const val AGENT_COOLDOWN_MILLIS = 45 * MINUTE
        private const val AI_REASONING_COOLDOWN_MILLIS = 6 * HOUR

        private val HUMAN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd MMM")
        private val REPORT_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm")
    }
}
