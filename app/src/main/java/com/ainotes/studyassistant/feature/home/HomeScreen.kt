package com.ainotes.studyassistant.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainotes.studyassistant.core.util.formatEpochDate

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val dashboard = viewModel.dashboard.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Study Dashboard",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "Subjects", value = dashboard.subjectCount.toString(), modifier = Modifier.weight(1f))
                StatCard(title = "Open Tasks", value = dashboard.openTaskCount.toString(), modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "Notes", value = dashboard.noteCount.toString(), modifier = Modifier.weight(1f))
                StatCard(title = "Files", value = dashboard.fileCount.toString(), modifier = Modifier.weight(1f))
            }
        }
        item {
            Text(
                text = "Upcoming Work",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (dashboard.upcomingTasks.isEmpty()) {
            item {
                Text(
                    text = "No upcoming tasks yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(dashboard.upcomingTasks, key = { it.id }) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                        Text("Due: ${formatEpochDate(task.dueAt)}", style = MaterialTheme.typography.bodySmall)
                        Text("Progress: ${task.progressPercent}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
