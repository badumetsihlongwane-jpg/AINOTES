package com.ainotes.studyassistant.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(
    private val repository: StudyRepository
) : ViewModel() {

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

    fun addTask(title: String, description: String, subjectId: Long?, dueAt: Long?, priority: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addTask(title, description, subjectId, dueAt, priority)
        }
    }

    fun updateProgress(taskId: Long, progress: Int, note: String) {
        viewModelScope.launch {
            repository.updateTaskProgress(taskId, progress, note)
        }
    }
}
