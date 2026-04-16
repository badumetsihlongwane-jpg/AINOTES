package com.ainotes.studyassistant.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ainotes.studyassistant.domain.StudyRepository
import com.ainotes.studyassistant.feature.files.FilesViewModel
import com.ainotes.studyassistant.feature.home.HomeViewModel
import com.ainotes.studyassistant.feature.notes.NotesViewModel
import com.ainotes.studyassistant.feature.progress.ProgressViewModel
import com.ainotes.studyassistant.feature.reminders.RemindersViewModel
import com.ainotes.studyassistant.feature.subjects.SubjectsViewModel
import com.ainotes.studyassistant.feature.tasks.TasksViewModel
import com.ainotes.studyassistant.feature.workspace.WorkspaceViewModel
import com.ainotes.studyassistant.notifications.ReminderScheduler

class StudyViewModelFactory(
    private val repository: StudyRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository) as T
            modelClass.isAssignableFrom(SubjectsViewModel::class.java) -> SubjectsViewModel(repository) as T
            modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(repository) as T
            modelClass.isAssignableFrom(NotesViewModel::class.java) -> NotesViewModel(repository) as T
            modelClass.isAssignableFrom(FilesViewModel::class.java) -> FilesViewModel(repository) as T
            modelClass.isAssignableFrom(RemindersViewModel::class.java) -> {
                RemindersViewModel(repository, reminderScheduler) as T
            }
            modelClass.isAssignableFrom(ProgressViewModel::class.java) -> ProgressViewModel(repository) as T
            modelClass.isAssignableFrom(WorkspaceViewModel::class.java) -> {
                WorkspaceViewModel(repository, reminderScheduler) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
