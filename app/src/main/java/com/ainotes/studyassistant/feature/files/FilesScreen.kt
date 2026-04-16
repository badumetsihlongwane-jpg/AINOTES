package com.ainotes.studyassistant.feature.files

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainotes.studyassistant.core.util.formatEpochDateTime

@Composable
fun FilesScreen(viewModel: FilesViewModel) {
    val context = LocalContext.current
    val files = viewModel.files.collectAsStateWithLifecycle().value
    val subjects = viewModel.subjects.collectAsStateWithLifecycle().value
    val tasks = viewModel.tasks.collectAsStateWithLifecycle().value

    var selectedName by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf("application/octet-stream") }
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var subjectIdInput by remember { mutableStateOf("") }
    var taskIdInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val metadata = resolveFileMetadata(context, uri.toString())
            selectedName = metadata.name
            mimeType = metadata.mimeType
            sizeBytes = metadata.sizeBytes
            selectedUri = uri.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Files", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Text("Pick File")
                    }
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = { selectedName = it },
                        label = { Text("File name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = selectedUri,
                        onValueChange = { selectedUri = it },
                        label = { Text("File URI") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mimeType,
                        onValueChange = { mimeType = it },
                        label = { Text("MIME type") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectIdInput,
                        onValueChange = { subjectIdInput = it },
                        label = { Text("Attach to subject ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskIdInput,
                        onValueChange = { taskIdInput = it },
                        label = { Text("Attach to task ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.addFile(
                                name = selectedName,
                                uri = selectedUri,
                                mimeType = mimeType,
                                sizeBytes = sizeBytes,
                                subjectId = subjectIdInput.toLongOrNull(),
                                taskId = taskIdInput.toLongOrNull()
                            )
                            selectedName = ""
                            selectedUri = ""
                        },
                        enabled = selectedName.isNotBlank() && selectedUri.isNotBlank()
                    ) {
                        Text("Save File Metadata")
                    }
                    if (subjects.isNotEmpty()) {
                        Text("Subjects: ${subjects.joinToString { "${it.id}:${it.name}" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (tasks.isNotEmpty()) {
                        Text("Tasks: ${tasks.take(5).joinToString { "${it.id}:${it.title}" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(files, key = { it.id }) { file ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium)
                    Text(file.mimeType)
                    Text("URI: ${file.uri}", style = MaterialTheme.typography.bodySmall)
                    Text("Uploaded: ${formatEpochDateTime(file.uploadedAt)}", style = MaterialTheme.typography.bodySmall)
                    file.sizeBytes?.let { Text("Size: $it bytes", style = MaterialTheme.typography.bodySmall) }
                    file.subjectId?.let { Text("Subject ID: $it", style = MaterialTheme.typography.bodySmall) }
                    file.taskId?.let { Text("Task ID: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private data class FileMetadata(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?
)

private fun resolveFileMetadata(context: Context, uri: String): FileMetadata {
    val parsed = android.net.Uri.parse(uri)
    var name = "Uploaded file"
    var size: Long? = null

    context.contentResolver.query(parsed, null, null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameColumn >= 0) name = cursor.getString(nameColumn)
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                size = cursor.getLong(sizeColumn)
            }
        }
    }

    val mime = context.contentResolver.getType(parsed).orEmpty().ifBlank { "application/octet-stream" }
    return FileMetadata(name = name, mimeType = mime, sizeBytes = size)
}
