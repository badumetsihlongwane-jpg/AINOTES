package com.ainotes.studyassistant.feature.workspace

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainotes.studyassistant.core.util.formatEpochDate
import com.ainotes.studyassistant.core.util.formatEpochDateTime
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import kotlinx.coroutines.launch

data class UploadDraft(
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long?
)

private enum class TaskSortLens {
    Soon,
    Module,
    Momentum
}

@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val assistantFeed = viewModel.assistantFeed.collectAsStateWithLifecycle().value
    val aiReady = viewModel.isAiReady
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var quickMenuExpanded by remember { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var pendingUpload by remember { mutableStateOf<UploadDraft?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var sortLens by remember { mutableStateOf(TaskSortLens.Soon) }

    val spotlightTasks = remember(state.tasks, state.subjects, sortLens) {
        when (sortLens) {
            TaskSortLens.Soon -> state.tasks
                .filter { !it.isCompleted }
                .sortedBy { it.dueAt ?: Long.MAX_VALUE }
                .take(5)

            TaskSortLens.Module -> {
                val subjectNames = state.subjects.associateBy({ it.id }, { it.name })
                state.tasks
                    .filter { !it.isCompleted }
                    .sortedWith(
                        compareBy<TaskEntity> { subjectNames[it.subjectId].orEmpty() }
                            .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    )
                    .take(5)
            }

            TaskSortLens.Momentum -> state.tasks
                .filter { !it.isCompleted }
                .sortedByDescending { it.progressPercent }
                .take(5)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingUpload = resolveUploadDraft(context, uri)
        showUploadDialog = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(visible = quickMenuExpanded) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniActionFab(label = "Subject", icon = Icons.Filled.School) {
                            showSubjectDialog = true
                            quickMenuExpanded = false
                        }
                        MiniActionFab(label = "Task", icon = Icons.Filled.Assignment) {
                            showTaskDialog = true
                            quickMenuExpanded = false
                        }
                        MiniActionFab(label = "Note", icon = Icons.Filled.EditNote) {
                            showNoteDialog = true
                            quickMenuExpanded = false
                        }
                        MiniActionFab(label = "Reminder", icon = Icons.Filled.Alarm) {
                            showReminderDialog = true
                            quickMenuExpanded = false
                        }
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = {
                        filePicker.launch(arrayOf("*/*"))
                    },
                    icon = { Icon(Icons.Filled.CloudUpload, contentDescription = "Upload context") },
                    text = { Text("Upload Context") },
                    containerColor = Color(0xFF3E6E4A),
                    contentColor = Color.White
                )

                SmallFloatingActionButton(
                    onClick = { quickMenuExpanded = !quickMenuExpanded },
                    containerColor = Color(0xFFFFF3E3),
                    contentColor = Color(0xFF3E6E4A)
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Quick create")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFFF8F3E8),
                            Color(0xFFECEDE6)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WorkspaceHeroCard(state = state, sortLens = sortLens, onSortLensChange = { sortLens = it })
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContextGatewayCard(
                        latestFileName = state.files.firstOrNull()?.name,
                        aiReady = aiReady
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    AssistantPulseCard(message = assistantFeed)
                }

                item {
                    CollageCard(title = "Focus Queue") {
                        if (spotlightTasks.isEmpty()) {
                            Text("No upcoming deadlines", style = MaterialTheme.typography.bodySmall)
                        } else {
                            spotlightTasks.take(3).forEach { task ->
                                Text(task.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Due ${formatEpochDate(task.dueAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4E4A44)
                                )
                                AssistChip(
                                    onClick = { viewModel.nudgeTaskProgress(task.id) },
                                    label = { Text("+15%") }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }

                item {
                    CollageCard(title = "Subjects") {
                        if (state.subjects.isEmpty()) {
                            Text("Create your first subject from quick actions.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.subjects.take(6).forEach { subject ->
                                Text(
                                    text = subject.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item {
                    CollageCard(title = "Notes") {
                        if (state.notes.isEmpty()) {
                            Text("No notes yet", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.notes.take(3).forEach { note ->
                                Text(note.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    note.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }

                item {
                    CollageCard(title = "Reminders") {
                        if (state.upcomingReminders.isEmpty()) {
                            Text("No reminders queued", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.upcomingReminders.take(3).forEach { reminder ->
                                Text(reminder.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    formatEpochDateTime(reminder.triggerAt),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    CollageCard(title = "Progress") {
                        Text(
                            "${state.completedTaskCount} completed of ${state.tasks.size} tasks",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { (state.averageProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .padding(top = 6.dp)
                        )
                        Text(
                            "Average progress ${state.averageProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSubjectDialog) {
        QuickInputDialog(
            title = "New Subject",
            firstLabel = "Subject name",
            secondLabel = "Description",
            onDismiss = { showSubjectDialog = false },
            onSubmit = { name, description, _ ->
                viewModel.addSubject(name, description)
                showSubjectDialog = false
            }
        )
    }

    if (showTaskDialog) {
        QuickInputDialog(
            title = "New Task",
            firstLabel = "Task title",
            secondLabel = "Subject ID (optional)",
            onDismiss = { showTaskDialog = false },
            onSubmit = { title, subjectIdText, _ ->
                viewModel.addTask(title = title, subjectId = subjectIdText.toLongOrNull())
                showTaskDialog = false
            }
        )
    }

    if (showNoteDialog) {
        QuickInputDialog(
            title = "New Note",
            firstLabel = "Title",
            secondLabel = "Body",
            thirdLabel = "Subject ID (optional)",
            onDismiss = { showNoteDialog = false },
            onSubmit = { title, body, subjectIdText ->
                viewModel.addNote(title, body, subjectIdText.toLongOrNull())
                showNoteDialog = false
            }
        )
    }

    if (showReminderDialog) {
        ReminderDialog(
            onDismiss = { showReminderDialog = false },
            onSubmit = { title, message, minutes, subjectId, taskId ->
                viewModel.addReminder(
                    title = title,
                    message = message,
                    delayMinutes = minutes,
                    subjectId = subjectId,
                    taskId = taskId
                )
                showReminderDialog = false
            }
        )
    }

    if (showUploadDialog && pendingUpload != null) {
        UploadContextDialog(
            draft = pendingUpload!!,
            onDismiss = {
                showUploadDialog = false
                pendingUpload = null
            },
            onSubmit = { subjectId, taskId, intentText, useAiAutoplan ->
                val draft = pendingUpload ?: return@UploadContextDialog
                viewModel.addUploadedContext(
                    name = draft.name,
                    uri = draft.uri,
                    mimeType = draft.mimeType,
                    sizeBytes = draft.sizeBytes,
                    subjectId = subjectId,
                    taskId = taskId,
                    intentText = intentText,
                    useAiAutoplan = useAiAutoplan
                )
                scope.launch {
                    val notice = if (useAiAutoplan) {
                        "Context uploaded. AI autopilot started."
                    } else {
                        "Context uploaded in manual mode."
                    }
                    snackbarHostState.showSnackbar(notice)
                }
                showUploadDialog = false
                pendingUpload = null
            }
        )
    }
}

@Composable
private fun WorkspaceHeroCard(
    state: WorkspaceUiState,
    sortLens: TaskSortLens,
    onSortLensChange: (TaskSortLens) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color(0xE6FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Study Workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Everything lives here: subjects, tasks, notes, files, reminders, and progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4A4741)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTag(label = "Subjects", value = state.subjects.size)
                StatTag(label = "Open", value = state.openTaskCount)
                StatTag(label = "Files", value = state.files.size)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onSortLensChange(TaskSortLens.Soon) },
                    label = { Text("Soon") },
                    enabled = sortLens != TaskSortLens.Soon
                )
                AssistChip(
                    onClick = { onSortLensChange(TaskSortLens.Module) },
                    label = { Text("Module") },
                    enabled = sortLens != TaskSortLens.Module
                )
                AssistChip(
                    onClick = { onSortLensChange(TaskSortLens.Momentum) },
                    label = { Text("Momentum") },
                    enabled = sortLens != TaskSortLens.Momentum
                )
            }
        }
    }
}

@Composable
private fun ContextGatewayCard(latestFileName: String?, aiReady: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B24), contentColor = Color(0xFFF8F5EE))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Context Gateway", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Upload any study material. The app stores metadata and intent notes so future AI actions can understand your learning context.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Latest upload: ${latestFileName ?: "None yet"}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD3CC)
            )
            Text(
                text = if (aiReady) "Autopilot mode: READY" else "Autopilot mode: NOT CONFIGURED",
                style = MaterialTheme.typography.bodySmall,
                color = if (aiReady) Color(0xFF9BEAAE) else Color(0xFFFFC9A7)
            )
        }
    }
}

@Composable
private fun AssistantPulseCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F0E6), contentColor = Color(0xFF2B2A26))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Assistant Pulse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "AI acts like a co-student: organizes content, tracks progress, and reports actions here.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CollageCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xD9FFFFFF)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            }
        )
    }
}

@Composable
private fun StatTag(label: String, value: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFD7)),
        modifier = Modifier.width(92.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MiniActionFab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xEFFFFFFF))) {
            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = Color(0xFFE9EFEA),
            contentColor = Color(0xFF2F5E3D)
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun QuickInputDialog(
    title: String,
    firstLabel: String,
    secondLabel: String,
    thirdLabel: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var third by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text(firstLabel) })
                OutlinedTextField(value = second, onValueChange = { second = it }, label = { Text(secondLabel) })
                if (thirdLabel != null) {
                    OutlinedTextField(value = third, onValueChange = { third = it }, label = { Text(thirdLabel) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(first, second, third) }, enabled = first.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReminderDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Long, Long?, Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    var subjectId by remember { mutableStateOf("") }
    var taskId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") })
                OutlinedTextField(value = minutes, onValueChange = { minutes = it }, label = { Text("In minutes") })
                OutlinedTextField(value = subjectId, onValueChange = { subjectId = it }, label = { Text("Subject ID (optional)") })
                OutlinedTextField(value = taskId, onValueChange = { taskId = it }, label = { Text("Task ID (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        title,
                        message,
                        minutes.toLongOrNull() ?: 60L,
                        subjectId.toLongOrNull(),
                        taskId.toLongOrNull()
                    )
                },
                enabled = title.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UploadContextDialog(
    draft: UploadDraft,
    onDismiss: () -> Unit,
    onSubmit: (Long?, Long?, String, Boolean) -> Unit
) {
    var intentText by remember {
        mutableStateOf("Use this file to support my study tasks and generate better plans later.")
    }
    var subjectId by remember { mutableStateOf("") }
    var taskId by remember { mutableStateOf("") }
    var autopilotEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Context Upload") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File: ${draft.name}", fontWeight = FontWeight.SemiBold)
                Text("Type: ${draft.mimeType}", style = MaterialTheme.typography.bodySmall)
                draft.sizeBytes?.let { Text("Size: $it bytes", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = intentText,
                    onValueChange = { intentText = it },
                    label = { Text("Learning intent") },
                    minLines = 3
                )
                OutlinedTextField(
                    value = subjectId,
                    onValueChange = { subjectId = it },
                    label = { Text("Subject ID (optional)") }
                )
                OutlinedTextField(
                    value = taskId,
                    onValueChange = { taskId = it },
                    label = { Text("Task ID (optional)") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Let AI auto-organize this upload")
                    Switch(
                        checked = autopilotEnabled,
                        onCheckedChange = { autopilotEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        subjectId.toLongOrNull(),
                        taskId.toLongOrNull(),
                        intentText,
                        autopilotEnabled
                    )
                }
            ) {
                Text("Save Context")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun resolveUploadDraft(context: Context, uri: Uri): UploadDraft {
    var fileName = "Uploaded file"
    var sizeBytes: Long? = null

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameColumn >= 0) {
                fileName = cursor.getString(nameColumn)
            }
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                sizeBytes = cursor.getLong(sizeColumn)
            }
        }
    }

    return UploadDraft(
        name = fileName,
        uri = uri.toString(),
        mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" },
        sizeBytes = sizeBytes
    )
}
