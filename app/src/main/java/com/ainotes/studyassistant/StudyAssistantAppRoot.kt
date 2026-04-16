package com.ainotes.studyassistant

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ainotes.studyassistant.core.AppContainer
import com.ainotes.studyassistant.core.StudyViewModelFactory
import com.ainotes.studyassistant.core.navigation.AppDestination
import com.ainotes.studyassistant.feature.files.FilesScreen
import com.ainotes.studyassistant.feature.files.FilesViewModel
import com.ainotes.studyassistant.feature.home.HomeScreen
import com.ainotes.studyassistant.feature.home.HomeViewModel
import com.ainotes.studyassistant.feature.notes.NotesScreen
import com.ainotes.studyassistant.feature.notes.NotesViewModel
import com.ainotes.studyassistant.feature.progress.ProgressScreen
import com.ainotes.studyassistant.feature.progress.ProgressViewModel
import com.ainotes.studyassistant.feature.reminders.RemindersScreen
import com.ainotes.studyassistant.feature.reminders.RemindersViewModel
import com.ainotes.studyassistant.feature.subjects.SubjectsScreen
import com.ainotes.studyassistant.feature.subjects.SubjectsViewModel
import com.ainotes.studyassistant.feature.tasks.TasksScreen
import com.ainotes.studyassistant.feature.tasks.TasksViewModel

@Composable
fun StudyAssistantAppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val destinations = remember { AppDestination.entries.toList() }
    val factory = remember { StudyViewModelFactory(container.repository, container.reminderScheduler) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Home.route) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(viewModel = vm)
            }
            composable(AppDestination.Subjects.route) {
                val vm: SubjectsViewModel = viewModel(factory = factory)
                SubjectsScreen(viewModel = vm)
            }
            composable(AppDestination.Tasks.route) {
                val vm: TasksViewModel = viewModel(factory = factory)
                TasksScreen(viewModel = vm)
            }
            composable(AppDestination.Notes.route) {
                val vm: NotesViewModel = viewModel(factory = factory)
                NotesScreen(viewModel = vm)
            }
            composable(AppDestination.Files.route) {
                val vm: FilesViewModel = viewModel(factory = factory)
                FilesScreen(viewModel = vm)
            }
            composable(AppDestination.Reminders.route) {
                val vm: RemindersViewModel = viewModel(factory = factory)
                RemindersScreen(viewModel = vm)
            }
            composable(AppDestination.Progress.route) {
                val vm: ProgressViewModel = viewModel(factory = factory)
                ProgressScreen(viewModel = vm)
            }
        }
    }
}
