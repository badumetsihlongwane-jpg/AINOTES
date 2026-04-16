package com.ainotes.studyassistant.ai

data class StudyAiInput(
    val fileName: String,
    val mimeType: String,
    val intentText: String,
    val existingSubjects: List<String>,
    val openTasks: List<String>,
    val upcomingDeadlines: List<String>
)

data class AiStudyPlanResult(
    val summary: String,
    val plan: AiStudyPlan,
    val rawResponse: String? = null,
    val isFallback: Boolean = false
)

data class AiStudyPlan(
    val subjects: List<AiSubjectPlan> = emptyList(),
    val tasks: List<AiTaskPlan> = emptyList(),
    val reminders: List<AiReminderPlan> = emptyList(),
    val notes: List<AiNotePlan> = emptyList(),
    val quizzes: List<AiQuizPlan> = emptyList(),
    val progressUpdates: List<AiProgressPlan> = emptyList()
)

data class AiSubjectPlan(
    val name: String,
    val description: String = ""
)

data class AiTaskPlan(
    val title: String,
    val description: String = "",
    val subjectName: String? = null,
    val dueAtEpochMillis: Long? = null,
    val priority: Int = 2,
    val progressPercent: Int? = null
)

data class AiReminderPlan(
    val title: String,
    val message: String = "",
    val subjectName: String? = null,
    val taskTitle: String? = null,
    val triggerAtEpochMillis: Long? = null
)

data class AiNotePlan(
    val title: String,
    val content: String,
    val subjectName: String? = null
)

data class AiQuizPlan(
    val title: String,
    val description: String = "",
    val subjectName: String? = null,
    val scheduledAtEpochMillis: Long? = null
)

data class AiProgressPlan(
    val taskTitle: String,
    val progressPercent: Int,
    val note: String = ""
)

interface StudyAiEngine {
    val isConfigured: Boolean

    suspend fun generatePlan(input: StudyAiInput): AiStudyPlanResult
}
