package com.ainotes.studyassistant.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = repository.notes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val subjects: StateFlow<List<SubjectEntity>> = repository.subjects.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun addNote(subjectId: Long?, title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repository.addNote(subjectId, title, content)
        }
    }
}
