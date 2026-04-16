package com.ainotes.studyassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ainotes.studyassistant.core.AppContainer
import com.ainotes.studyassistant.core.StudyViewModelFactory
import com.ainotes.studyassistant.feature.workspace.WorkspaceScreen
import com.ainotes.studyassistant.feature.workspace.WorkspaceViewModel

@Composable
fun StudyAssistantAppRoot(container: AppContainer) {
    val factory = remember {
        StudyViewModelFactory(
            repository = container.repository,
            reminderScheduler = container.reminderScheduler,
            aiEngine = container.aiEngine
        )
    }
    val vm: WorkspaceViewModel = viewModel(factory = factory)
    WorkspaceScreen(viewModel = vm)
}
