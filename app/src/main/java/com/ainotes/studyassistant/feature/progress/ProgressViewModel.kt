package com.ainotes.studyassistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(repository: StudyRepository) : ViewModel() {

    val logs: StateFlow<List<ProgressLogEntity>> = repository.progressLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val tasks: StateFlow<List<TaskEntity>> = repository.tasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
}
