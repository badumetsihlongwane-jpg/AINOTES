package com.ainotes.studyassistant.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainotes.studyassistant.core.util.formatEpochDate

@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val tasks = viewModel.tasks.collectAsStateWithLifecycle().value
    val subjects = viewModel.subjects.collectAsStateWithLifecycle().value

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var subjectIdInput by remember { mutableStateOf("") }
    var dueInDaysInput by remember { mutableStateOf("7") }
    var priorityInput by remember { mutableStateOf("2") }

    val taskProgressEdits = remember { mutableStateMapOf<Long, Float>() }
    val taskNotes = remember { mutableStateMapOf<Long, String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Tasks & Assignments", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectIdInput,
                        onValueChange = { subjectIdInput = it },
                        label = { Text("Attach to subject ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dueInDaysInput,
                        onValueChange = { dueInDaysInput = it },
                        label = { Text("Due in days") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priorityInput,
                        onValueChange = { priorityInput = it },
                        label = { Text("Priority 1-3") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val dueDays = dueInDaysInput.toLongOrNull() ?: 7L
                            val dueAt = System.currentTimeMillis() + dueDays * 24 * 60 * 60 * 1000
                            viewModel.addTask(
                                title = title,
                                description = description,
                                subjectId = subjectIdInput.toLongOrNull(),
                                dueAt = dueAt,
                                priority = priorityInput.toIntOrNull() ?: 2
                            )
                            title = ""
                            description = ""
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text("Add Task")
                    }
                    if (subjects.isNotEmpty()) {
                        Text(
                            text = "Subjects: ${subjects.joinToString { "${it.id}:${it.name}" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        items(tasks, key = { it.id }) { task ->
            val sliderValue = taskProgressEdits[task.id] ?: task.progressPercent.toFloat()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(task.description.ifBlank { "No description" })
                    Text("Due: ${formatEpochDate(task.dueAt)}")
                    Text("Priority: ${task.priority}")
                    Text("Progress: ${sliderValue.toInt()}%")
                    Slider(
                        value = sliderValue,
                        onValueChange = { taskProgressEdits[task.id] = it },
                        valueRange = 0f..100f
                    )
                    OutlinedTextField(
                        value = taskNotes[task.id] ?: "",
                        onValueChange = { taskNotes[task.id] = it },
                        label = { Text("Progress note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.updateProgress(task.id, sliderValue.toInt(), taskNotes[task.id].orEmpty())
                        }) {
                            Text("Save Progress")
                        }
                    }
                }
            }
        }
    }
}
