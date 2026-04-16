package com.ainotes.studyassistant.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.ReminderEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.domain.StudyRepository
import com.ainotes.studyassistant.notifications.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val repository: StudyRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val tasks: StateFlow<List<TaskEntity>> = repository.tasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val subjects: StateFlow<List<SubjectEntity>> = repository.subjects.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun addReminder(
        title: String,
        message: String,
        triggerAt: Long,
        taskId: Long?,
        subjectId: Long?
    ) {
        if (title.isBlank() || triggerAt <= System.currentTimeMillis()) return
        viewModelScope.launch {
            val id = repository.addReminder(title, message, triggerAt, taskId, subjectId)
            reminderScheduler.schedule(id, title, message, triggerAt)
        }
    }
}
