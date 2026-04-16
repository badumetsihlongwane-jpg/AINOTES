package com.ainotes.studyassistant.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainotes.studyassistant.core.util.formatEpochDateTime

@Composable
fun RemindersScreen(viewModel: RemindersViewModel) {
    val reminders = viewModel.reminders.collectAsStateWithLifecycle().value
    val tasks = viewModel.tasks.collectAsStateWithLifecycle().value
    val subjects = viewModel.subjects.collectAsStateWithLifecycle().value

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var delayMinutes by remember { mutableStateOf("60") }
    var taskIdInput by remember { mutableStateOf("") }
    var subjectIdInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Reminders", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Reminder title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = delayMinutes,
                        onValueChange = { delayMinutes = it },
                        label = { Text("Trigger in minutes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { delayMinutes = "30" }, label = { Text("30m") })
                        AssistChip(onClick = { delayMinutes = "120" }, label = { Text("2h") })
                        AssistChip(onClick = { delayMinutes = "1440" }, label = { Text("1d") })
                    }
                    OutlinedTextField(
                        value = taskIdInput,
                        onValueChange = { taskIdInput = it },
                        label = { Text("Attach to task ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectIdInput,
                        onValueChange = { subjectIdInput = it },
                        label = { Text("Attach to subject ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val minutes = delayMinutes.toLongOrNull() ?: 60L
                            val triggerAt = System.currentTimeMillis() + minutes * 60 * 1000
                            viewModel.addReminder(
                                title = title,
                                message = message.ifBlank { "Study reminder" },
                                triggerAt = triggerAt,
                                taskId = taskIdInput.toLongOrNull(),
                                subjectId = subjectIdInput.toLongOrNull()
                            )
                            title = ""
                            message = ""
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text("Create Reminder")
                    }
                    if (tasks.isNotEmpty()) {
                        Text("Tasks: ${tasks.take(6).joinToString { "${it.id}:${it.title}" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (subjects.isNotEmpty()) {
                        Text("Subjects: ${subjects.joinToString { "${it.id}:${it.name}" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(reminders, key = { it.id }) { reminder ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                    Text(reminder.message)
                    Text("At: ${formatEpochDateTime(reminder.triggerAt)}", style = MaterialTheme.typography.bodySmall)
                    reminder.taskId?.let { Text("Task ID: $it", style = MaterialTheme.typography.bodySmall) }
                    reminder.subjectId?.let { Text("Subject ID: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
