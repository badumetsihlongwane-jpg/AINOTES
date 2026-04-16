package com.ainotes.studyassistant.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.ainotes.studyassistant.core.util.formatEpochDateTime
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val logs = viewModel.logs.collectAsStateWithLifecycle().value
    val tasks = viewModel.tasks.collectAsStateWithLifecycle().value

    val avgProgress = if (tasks.isEmpty()) 0 else tasks.map { it.progressPercent }.average().roundToInt()
    val completed = tasks.count { it.isCompleted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Progress", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Average task progress: $avgProgress%", style = MaterialTheme.typography.titleMedium)
                    Text("Completed tasks: $completed / ${tasks.size}")
                    Text("Progress updates logged: ${logs.size}")
                }
            }
        }

        items(logs, key = { it.id }) { log ->
            val taskTitle = tasks.firstOrNull { it.id == log.taskId }?.title ?: "Task ${log.taskId}"
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(taskTitle, style = MaterialTheme.typography.titleMedium)
                    Text("Progress: ${log.progressPercent}%")
                    if (log.note.isNotBlank()) {
                        Text("Note: ${log.note}")
                    }
                    Text("Logged: ${formatEpochDateTime(log.loggedAt)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
