package com.ainotes.studyassistant.feature.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubjectsViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    val subjects: StateFlow<List<SubjectEntity>> = repository.subjects.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun addSubject(name: String, description: String, colorHex: String = "#2E7D32") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addSubject(name, description, colorHex)
        }
    }
}
