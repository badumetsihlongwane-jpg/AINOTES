package com.ainotes.studyassistant.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity
import com.ainotes.studyassistant.domain.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FilesViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    val files: StateFlow<List<UploadedFileEntity>> = repository.files.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val subjects: StateFlow<List<SubjectEntity>> = repository.subjects.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val tasks: StateFlow<List<TaskEntity>> = repository.tasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun addFile(
        name: String,
        uri: String,
        mimeType: String,
        sizeBytes: Long?,
        subjectId: Long?,
        taskId: Long?
    ) {
        if (name.isBlank() || uri.isBlank()) return
        viewModelScope.launch {
            repository.addFile(name, uri, mimeType, sizeBytes, subjectId, taskId)
        }
    }
}
