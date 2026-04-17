package com.ainotes.studyassistant.feature.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainotes.studyassistant.core.util.formatEpochDate
import com.ainotes.studyassistant.core.util.formatEpochDateTime
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UploadDraft(
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long?
)

private enum class FeaturePanel {
    Tasks,
    Notes,
    Reminders,
    Subjects,
    Files,
    Progress
}

private data class FeatureCardUi(
    val panel: FeaturePanel,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val stat: String,
    val preview: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val assistantFeed = viewModel.assistantFeed.collectAsStateWithLifecycle().value
    val aiReady = viewModel.isAiReady
    val isAgentWorking = viewModel.isAgentWorking.collectAsStateWithLifecycle().value

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedPanel by remember { mutableStateOf<FeaturePanel?>(null) }

    var showSubjectDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    var pendingUpload by remember { mutableStateOf<UploadDraft?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onDashboardOpened()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Provider may not support persistable URI access.
        }
        pendingUpload = resolveUploadDraft(context, uri)
        showUploadDialog = true
    }

    val cards = buildFeatureCards(state)
    val todayLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM"))
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Filled.CloudUpload, contentDescription = "Upload context") },
                text = { Text("Upload Context") },
                containerColor = Color(0xFF255C43),
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    DashboardHeader(
                        todayLabel = todayLabel,
                        openTaskCount = state.openTaskCount,
                        upcomingReminderCount = state.upcomingReminders.size
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    AgentStatusCard(
                        aiReady = aiReady,
                        isAgentWorking = isAgentWorking,
                        message = assistantFeed,
                        onRunCheckIn = { viewModel.runAgentCheckIn(force = true) }
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    QuickActionBar(
                        onAddSubject = { showSubjectDialog = true },
                        onAddTask = { showTaskDialog = true },
                        onAddNote = { showNoteDialog = true },
                        onAddReminder = { showReminderDialog = true }
                    )
                }

                items(cards, key = { it.title }) { card ->
                    DashboardFeatureCard(
                        card = card,
                        onClick = { selectedPanel = card.panel }
                    )
                }
            }
        }
    }

    selectedPanel?.let { panel ->
        ModalBottomSheet(onDismissRequest = { selectedPanel = null }) {
            FeatureDetailSheet(
                panel = panel,
                state = state,
                onNudgeProgress = { taskId -> viewModel.nudgeTaskProgress(taskId) }
            )
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
                    val message = if (useAiAutoplan) {
                        "Context uploaded. Agent started autonomous planning."
                    } else {
                        "Context uploaded in manual mode."
                    }
                    snackbarHostState.showSnackbar(message)
                }
                showUploadDialog = false
                pendingUpload = null
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    todayLabel: String,
    openTaskCount: Int,
    upcomingReminderCount: Int
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Study Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = todayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBadge(label = "Open tasks", value = openTaskCount)
                StatBadge(label = "Upcoming", value = upcomingReminderCount)
            }
        }
    }
}

@Composable
private fun AgentStatusCard(
    aiReady: Boolean,
    isAgentWorking: Boolean,
    message: String,
    onRunCheckIn: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (aiReady) {
                Color(0xFF1F2F27)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (aiReady) Color(0xFFF3F8F4) else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Agent")
                Text(
                    text = "AI Study Agent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (aiReady) "Connected" else "Rule Mode")
                    }
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Button(onClick = onRunCheckIn, enabled = !isAgentWorking) {
                Text(if (isAgentWorking) "Running Check-In..." else "Run Agent Check-In")
            }
        }
    }
}

@Composable
private fun QuickActionBar(
    onAddSubject: () -> Unit,
    onAddTask: () -> Unit,
    onAddNote: () -> Unit,
    onAddReminder: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = onAddSubject, label = { Text("+ Subject") })
            AssistChip(onClick = onAddTask, label = { Text("+ Task") })
            AssistChip(onClick = onAddNote, label = { Text("+ Note") })
            AssistChip(onClick = onAddReminder, label = { Text("+ Reminder") })
        }
    }
}

@Composable
private fun DashboardFeatureCard(
    card: FeatureCardUi,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
        label = "card_scale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) {
            card.accent.copy(alpha = 0.28f)
        } else {
            card.accent.copy(alpha = 0.16f)
        },
        label = "card_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(182.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = card.title,
                        tint = card.accent
                    )
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Card(colors = CardDefaults.cardColors(containerColor = card.accent.copy(alpha = 0.18f))) {
                    Text(
                        text = card.stat,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = card.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            card.preview.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun FeatureDetailSheet(
    panel: FeaturePanel,
    state: WorkspaceUiState,
    onNudgeProgress: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = when (panel) {
                FeaturePanel.Tasks -> "Tasks"
                FeaturePanel.Notes -> "Notes"
                FeaturePanel.Reminders -> "Reminders"
                FeaturePanel.Subjects -> "Subjects"
                FeaturePanel.Files -> "Files"
                FeaturePanel.Progress -> "Progress"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        when (panel) {
            FeaturePanel.Tasks -> {
                val tasks = state.tasks
                    .sortedBy { it.dueAt ?: Long.MAX_VALUE }
                    .take(40)

                if (tasks.isEmpty()) {
                    Text("No tasks yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                    Text("Due ${formatEpochDate(task.dueAt)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Progress ${task.progressPercent}%", style = MaterialTheme.typography.bodySmall)
                                    LinearProgressIndicator(
                                        progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    AssistChip(
                                        onClick = { onNudgeProgress(task.id) },
                                        label = { Text("+15% progress") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Notes -> {
                val notes = state.notes.sortedByDescending { it.updatedAt }.take(40)
                if (notes.isEmpty()) {
                    Text("No notes yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(notes, key = { it.id }) { note ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(note.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        note.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Updated ${formatEpochDateTime(note.updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Reminders -> {
                val reminders = state.reminders.sortedBy { it.triggerAt }.take(40)
                if (reminders.isEmpty()) {
                    Text("No reminders set yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(reminders, key = { it.id }) { reminder ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(reminder.title, fontWeight = FontWeight.SemiBold)
                                    Text(reminder.message, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = formatEpochDateTime(reminder.triggerAt),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Subjects -> {
                val taskCountBySubject = state.tasks.groupBy { it.subjectId }
                val subjects = state.subjects.sortedBy { it.name }
                if (subjects.isEmpty()) {
                    Text("No subjects yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(subjects, key = { it.id }) { subject ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(subject.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = subject.description.ifBlank { "No description" },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Tasks: ${taskCountBySubject[subject.id]?.size ?: 0}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Files -> {
                val files = state.files.sortedByDescending { it.uploadedAt }.take(40)
                if (files.isEmpty()) {
                    Text("No uploaded files yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(files, key = { it.id }) { file ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(file.name, fontWeight = FontWeight.SemiBold)
                                    Text(file.mimeType, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "Uploaded ${formatEpochDateTime(file.uploadedAt)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Progress -> {
                val avg = state.averageProgress
                val logs = state.progressLogs.sortedByDescending { it.loggedAt }.take(30)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Completed ${state.completedTaskCount} / ${state.tasks.size} tasks")
                    LinearProgressIndicator(
                        progress = { (avg / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Average progress: $avg%", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    if (logs.isEmpty()) {
                        Text("No progress logs yet.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(logs, key = { it.id }) { log ->
                                Text(
                                    text = "${log.progressPercent}% - ${log.note.ifBlank { "Update" }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
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
                Text("Save")
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
        mutableStateOf("Analyze this study material and autonomously improve my plan.")
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
                    label = { Text("Agent directive") },
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
                    Text("Enable autonomous AI planning")
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
                Text("Save")
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

private fun buildFeatureCards(state: WorkspaceUiState): List<FeatureCardUi> {
    val tasksPreview = state.tasks
        .filter { !it.isCompleted }
        .sortedBy { it.dueAt ?: Long.MAX_VALUE }
        .take(2)
        .map { "${it.title} • ${formatEpochDate(it.dueAt)}" }
        .ifEmpty { listOf("No open tasks") }

    val notesPreview = state.notes
        .sortedByDescending { it.updatedAt }
        .take(2)
        .map { note ->
            if (note.content.isBlank()) note.title else "${note.title}: ${note.content.take(48)}"
        }
        .ifEmpty { listOf("No notes yet") }

    val remindersPreview = state.upcomingReminders
        .take(2)
        .map { "${it.title} • ${formatEpochDateTime(it.triggerAt)}" }
        .ifEmpty { listOf("No upcoming reminders") }

    val subjectsPreview = state.subjects
        .take(2)
        .map { it.name }
        .ifEmpty { listOf("No subjects yet") }

    val filesPreview = state.files
        .sortedByDescending { it.uploadedAt }
        .take(2)
        .map { it.name }
        .ifEmpty { listOf("No files uploaded") }

    val progressPreview = listOf(
        "Average progress ${state.averageProgress}%",
        "Completed ${state.completedTaskCount}/${state.tasks.size} tasks"
    )

    return listOf(
        FeatureCardUi(
            panel = FeaturePanel.Tasks,
            title = "Tasks",
            icon = Icons.Filled.AssignmentTurnedIn,
            accent = Color(0xFF2D9B63),
            stat = state.openTaskCount.toString(),
            preview = tasksPreview
        ),
        FeatureCardUi(
            panel = FeaturePanel.Notes,
            title = "Notes",
            icon = Icons.Filled.Article,
            accent = Color(0xFF2F80ED),
            stat = state.notes.size.toString(),
            preview = notesPreview
        ),
        FeatureCardUi(
            panel = FeaturePanel.Reminders,
            title = "Reminders",
            icon = Icons.Filled.Alarm,
            accent = Color(0xFFF2994A),
            stat = state.upcomingReminders.size.toString(),
            preview = remindersPreview
        ),
        FeatureCardUi(
            panel = FeaturePanel.Subjects,
            title = "Subjects",
            icon = Icons.Filled.School,
            accent = Color(0xFF5C6F82),
            stat = state.subjects.size.toString(),
            preview = subjectsPreview
        ),
        FeatureCardUi(
            panel = FeaturePanel.Files,
            title = "Files",
            icon = Icons.Filled.Folder,
            accent = Color(0xFF1F9E96),
            stat = state.files.size.toString(),
            preview = filesPreview
        ),
        FeatureCardUi(
            panel = FeaturePanel.Progress,
            title = "Progress",
            icon = Icons.Filled.ShowChart,
            accent = Color(0xFFE06464),
            stat = "${state.averageProgress}%",
            preview = progressPreview
        )
    )
}
