package com.ainotes.studyassistant.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.domain.DashboardSnapshot
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: StudyRepository) : ViewModel() {
    val dashboard: StateFlow<DashboardSnapshot> = repository.dashboard.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardSnapshot(
            subjectCount = 0,
            openTaskCount = 0,
            noteCount = 0,
            fileCount = 0,
            upcomingTasks = emptyList()
        )
    )
}
