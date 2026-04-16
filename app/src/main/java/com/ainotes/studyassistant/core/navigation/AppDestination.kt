package com.ainotes.studyassistant.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home(route = "home", label = "Home", icon = Icons.Filled.Dashboard),
    Subjects(route = "subjects", label = "Subjects", icon = Icons.Filled.School),
    Tasks(route = "tasks", label = "Tasks", icon = Icons.Filled.CheckCircle),
    Notes(route = "notes", label = "Notes", icon = Icons.Filled.Description),
    Files(route = "files", label = "Files", icon = Icons.Filled.Folder),
    Reminders(route = "reminders", label = "Reminders", icon = Icons.Filled.Notifications),
    Progress(route = "progress", label = "Progress", icon = Icons.Filled.ShowChart)
}
