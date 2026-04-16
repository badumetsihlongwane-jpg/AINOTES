package com.ainotes.studyassistant.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun NotesScreen(viewModel: NotesViewModel) {
    val notes = viewModel.notes.collectAsStateWithLifecycle().value
    val subjects = viewModel.subjects.collectAsStateWithLifecycle().value

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var subjectIdInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Notes", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Note body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = subjectIdInput,
                        onValueChange = { subjectIdInput = it },
                        label = { Text("Attach to subject ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.addNote(subjectIdInput.toLongOrNull(), title, content)
                            title = ""
                            content = ""
                        },
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text("Add Note")
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

        items(notes, key = { it.id }) { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    Text(note.content)
                    Text("Updated: ${formatEpochDateTime(note.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                    note.subjectId?.let { Text("Subject ID: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
