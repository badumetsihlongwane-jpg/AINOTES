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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
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
import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

data class UploadDraft(
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long?
)

private enum class FeaturePanel {
    Focus,
    Assessments,
    Modules,
    AiPlan,
    Reminders,
    Library
}

private data class OverviewCardUi(
    val panel: FeaturePanel,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val stat: String,
    val preview: List<String>
)

private data class FocusItemUi(
    val taskId: Long,
    val title: String,
    val moduleName: String,
    val typeLabel: String,
    val urgencyLabel: String,
    val progressPercent: Int,
    val dueAt: Long?,
    val description: String
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

    val subjectNameById = remember(state.subjects) {
        state.subjects.associate { it.id to it.name }
    }
    val focusItems = remember(state.tasks, state.subjects) {
        buildFocusItems(state.tasks, subjectNameById)
    }
    val assessmentTasks = remember(state.tasks) {
        state.tasks.filter { task -> !task.isCompleted && classifyTaskType(task).contains("assessment", ignoreCase = true) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }
    }
    val aiReports = remember(state.notes) {
        state.notes.filter { note ->
            val title = note.title.lowercase()
            title.startsWith("ai ") || title.startsWith("agent ") || title.contains("report")
        }.sortedByDescending { it.updatedAt }
    }

    val overviewCards = buildOverviewCards(
        state = state,
        subjectNameById = subjectNameById,
        assistantFeed = assistantFeed,
        focusItems = focusItems,
        assessmentTasks = assessmentTasks,
        aiReports = aiReports
    )

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
                text = { Text("Upload Unit / Plan") },
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
                    DashboardHeroCard(
                        todayLabel = todayLabel,
                        focusCount = focusItems.size,
                        assessmentCount = assessmentTasks.size,
                        openTaskCount = state.openTaskCount
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    FocusNowCard(
                        items = focusItems.take(3),
                        onOpenAll = { selectedPanel = FeaturePanel.Focus }
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

                items(overviewCards, key = { it.title }) { card ->
                    OverviewFeatureCard(
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
                subjectNameById = subjectNameById,
                assistantFeed = assistantFeed,
                focusItems = focusItems,
                assessmentTasks = assessmentTasks,
                aiReports = aiReports,
                onNudgeProgress = { taskId -> viewModel.nudgeTaskProgress(taskId) }
            )
        }
    }

    if (showSubjectDialog) {
        QuickInputDialog(
            title = "New Module",
            firstLabel = "Module name",
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
            title = "New Study Action",
            firstLabel = "Action title",
            secondLabel = "Module ID (optional)",
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
            thirdLabel = "Module ID (optional)",
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
                        "Context uploaded. Agent planning started."
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
private fun DashboardHeroCard(
    todayLabel: String,
    focusCount: Int,
    assessmentCount: Int,
    openTaskCount: Int
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
                text = "Study Command Center",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = todayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBadge(label = "Focus now", value = focusCount)
                StatBadge(label = "Assessments", value = assessmentCount)
                StatBadge(label = "Open", value = openTaskCount)
            }
        }
    }
}

@Composable
private fun FocusNowCard(
    items: List<FocusItemUi>,
    onOpenAll: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Focus Now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenAll) {
                    Text("View all")
                }
            }
            if (items.isEmpty()) {
                Text("No urgent items. Upload unit plans or add modules to let the agent build your day.")
            } else {
                items.forEach { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text("${item.typeLabel} | ${item.moduleName}", style = MaterialTheme.typography.bodySmall)
                            Text(item.urgencyLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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
                    text = "AI Planner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (aiReady) "Connected" else "Rule mode")
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
                Text(if (isAgentWorking) "Building plan..." else "Run Morning Plan")
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
            AssistChip(onClick = onAddSubject, label = { Text("+ Module") })
            AssistChip(onClick = onAddTask, label = { Text("+ Action") })
            AssistChip(onClick = onAddNote, label = { Text("+ Note") })
            AssistChip(onClick = onAddReminder, label = { Text("+ Reminder") })
        }
    }
}

@Composable
private fun OverviewFeatureCard(
    card: OverviewCardUi,
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
            .height(186.dp)
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
    subjectNameById: Map<Long, String>,
    assistantFeed: String,
    focusItems: List<FocusItemUi>,
    assessmentTasks: List<TaskEntity>,
    aiReports: List<NoteEntity>,
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
                FeaturePanel.Focus -> "Focus Actions"
                FeaturePanel.Assessments -> "Assessments"
                FeaturePanel.Modules -> "Modules"
                FeaturePanel.AiPlan -> "AI Plan"
                FeaturePanel.Reminders -> "Reminders"
                FeaturePanel.Library -> "Library"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        when (panel) {
            FeaturePanel.Focus -> {
                if (focusItems.isEmpty()) {
                    Text("No focus actions yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(focusItems, key = { it.taskId }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold)
                                    Text("${item.typeLabel} | ${item.moduleName}", style = MaterialTheme.typography.bodySmall)
                                    Text(item.urgencyLabel, style = MaterialTheme.typography.bodySmall)
                                    if (item.description.isNotBlank()) {
                                        Text(item.description, style = MaterialTheme.typography.labelSmall)
                                    }
                                    LinearProgressIndicator(
                                        progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    AssistChip(
                                        onClick = { onNudgeProgress(item.taskId) },
                                        label = { Text("+15% done") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Assessments -> {
                if (assessmentTasks.isEmpty()) {
                    Text("No quiz/test tasks detected yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(assessmentTasks, key = { it.id }) { task ->
                            val moduleName = task.subjectId?.let { subjectNameById[it] } ?: "Unassigned"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                    Text(moduleName, style = MaterialTheme.typography.bodySmall)
                                    Text(relativeDueLabel(task.dueAt), style = MaterialTheme.typography.bodySmall)
                                    Text("Progress ${task.progressPercent}%", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Modules -> {
                val openTasksBySubject = state.tasks.filter { !it.isCompleted }.groupBy { it.subjectId }
                val assessmentBySubject = assessmentTasks.groupBy { it.subjectId }

                if (state.subjects.isEmpty()) {
                    Text("No modules yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.subjects, key = { it.id }) { subject ->
                            val moduleTasks = openTasksBySubject[subject.id].orEmpty()
                            val moduleAssessments = assessmentBySubject[subject.id].orEmpty()
                            val moduleAvg = if (moduleTasks.isEmpty()) 0 else moduleTasks.map { it.progressPercent }.average().toInt()

                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(subject.name, fontWeight = FontWeight.SemiBold)
                                    Text(subject.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Open actions ${moduleTasks.size} | Assessments ${moduleAssessments.size}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    LinearProgressIndicator(
                                        progress = { (moduleAvg / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Module completion $moduleAvg%", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.AiPlan -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Latest agent message", fontWeight = FontWeight.SemiBold)
                            Text(assistantFeed, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (aiReports.isEmpty()) {
                        Text("No AI reports yet.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(aiReports.take(20), key = { it.id }) { report ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(report.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            report.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("Updated ${formatEpochDateTime(report.updatedAt)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Reminders -> {
                val reminders = state.reminders.sortedBy { it.triggerAt }
                if (reminders.isEmpty()) {
                    Text("No reminders set yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(reminders, key = { it.id }) { reminder ->
                            val moduleName = reminder.subjectId?.let { subjectNameById[it] } ?: "General"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(reminder.title, fontWeight = FontWeight.SemiBold)
                                    Text(moduleName, style = MaterialTheme.typography.bodySmall)
                                    Text(reminder.message, style = MaterialTheme.typography.bodySmall)
                                    Text(formatEpochDateTime(reminder.triggerAt), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            FeaturePanel.Library -> {
                val files = state.files.sortedByDescending { it.uploadedAt }
                if (files.isEmpty()) {
                    Text("No uploaded files yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(files, key = { it.id }) { file ->
                            val moduleName = file.subjectId?.let { subjectNameById[it] } ?: "General"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(file.name, fontWeight = FontWeight.SemiBold)
                                    Text(moduleName, style = MaterialTheme.typography.bodySmall)
                                    Text(file.mimeType, style = MaterialTheme.typography.bodySmall)
                                    Text("Uploaded ${formatEpochDateTime(file.uploadedAt)}", style = MaterialTheme.typography.labelSmall)
                                }
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
                OutlinedTextField(value = subjectId, onValueChange = { subjectId = it }, label = { Text("Module ID (optional)") })
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
        mutableStateOf(
            "Read this unit plan and build a clear module-based study plan with upcoming tests, quizzes, reminders, and no duplicate actions."
        )
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
                    label = { Text("Module ID (optional)") }
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

private fun buildOverviewCards(
    state: WorkspaceUiState,
    subjectNameById: Map<Long, String>,
    assistantFeed: String,
    focusItems: List<FocusItemUi>,
    assessmentTasks: List<TaskEntity>,
    aiReports: List<NoteEntity>
): List<OverviewCardUi> {
    val modulePreview = state.subjects
        .sortedBy { it.name }
        .take(2)
        .map { subject ->
            val openCount = state.tasks.count { it.subjectId == subject.id && !it.isCompleted }
            "${subject.name}: $openCount open"
        }
        .ifEmpty { listOf("No modules configured") }

    val assessmentPreview = assessmentTasks
        .take(2)
        .map { task ->
            val moduleName = task.subjectId?.let { subjectNameById[it] } ?: "General"
            "${task.title} | $moduleName | ${relativeDueLabel(task.dueAt)}"
        }
        .ifEmpty { listOf("No quiz or test tasks detected") }

    val reminderPreview = state.upcomingReminders
        .take(2)
        .map { "${it.title} | ${formatEpochDateTime(it.triggerAt)}" }
        .ifEmpty { listOf("No upcoming reminders") }

    val libraryPreview = state.files
        .sortedByDescending { it.uploadedAt }
        .take(2)
        .map { it.name }
        .ifEmpty { listOf("No uploaded unit docs") }

    val planPreview = buildList {
        add(assistantFeed.lines().firstOrNull().orEmpty().ifBlank { "Agent has no report yet" })
        add(aiReports.firstOrNull()?.title ?: "Run morning plan to generate summary")
    }

    val focusPreview = focusItems
        .take(2)
        .map { "${it.title} | ${it.urgencyLabel}" }
        .ifEmpty { listOf("No urgent actions") }

    return listOf(
        OverviewCardUi(
            panel = FeaturePanel.Focus,
            title = "Study Actions",
            icon = Icons.Filled.AssignmentTurnedIn,
            accent = Color(0xFF2D9B63),
            stat = focusItems.size.toString(),
            preview = focusPreview
        ),
        OverviewCardUi(
            panel = FeaturePanel.Assessments,
            title = "Tests and Quizzes",
            icon = Icons.Filled.ShowChart,
            accent = Color(0xFF2F80ED),
            stat = assessmentTasks.size.toString(),
            preview = assessmentPreview
        ),
        OverviewCardUi(
            panel = FeaturePanel.Modules,
            title = "Modules",
            icon = Icons.Filled.School,
            accent = Color(0xFF5C6F82),
            stat = state.subjects.size.toString(),
            preview = modulePreview
        ),
        OverviewCardUi(
            panel = FeaturePanel.AiPlan,
            title = "AI Plan",
            icon = Icons.Filled.AutoAwesome,
            accent = Color(0xFF967C2F),
            stat = aiReports.size.toString(),
            preview = planPreview
        ),
        OverviewCardUi(
            panel = FeaturePanel.Reminders,
            title = "Reminders",
            icon = Icons.Filled.Alarm,
            accent = Color(0xFFF2994A),
            stat = state.upcomingReminders.size.toString(),
            preview = reminderPreview
        ),
        OverviewCardUi(
            panel = FeaturePanel.Library,
            title = "Unit Library",
            icon = Icons.Filled.Folder,
            accent = Color(0xFF1F9E96),
            stat = state.files.size.toString(),
            preview = libraryPreview
        )
    )
}

private fun buildFocusItems(
    tasks: List<TaskEntity>,
    subjectNameById: Map<Long, String>
): List<FocusItemUi> {
    val now = System.currentTimeMillis()

    return tasks
        .filter { !it.isCompleted }
        .map { task ->
            val moduleName = task.subjectId?.let { subjectNameById[it] } ?: "General"
            val type = classifyTaskType(task)
            val urgency = relativeDueLabel(task.dueAt)
            FocusItemUi(
                taskId = task.id,
                title = task.title,
                moduleName = moduleName,
                typeLabel = type,
                urgencyLabel = urgency,
                progressPercent = task.progressPercent,
                dueAt = task.dueAt,
                description = task.description
            )
        }
        .sortedWith(
            compareBy<FocusItemUi> {
                // Assessment items should bubble up first.
                if (it.typeLabel.contains("assessment", ignoreCase = true)) 0 else 1
            }.thenBy {
                it.dueAt ?: Long.MAX_VALUE
            }.thenBy {
                it.progressPercent
            }
        )
        .filter { item ->
            val due = item.dueAt
            due == null || due <= now + 14L * 24L * 60L * 60L * 1000L || item.progressPercent < 50
        }
}

private fun classifyTaskType(task: TaskEntity): String {
    val text = "${task.title} ${task.description}".lowercase()
    return when {
        text.contains("quiz") || text.contains("test") || text.contains("exam") || text.contains("assessment") -> "Assessment"
        text.contains("assignment") || text.contains("submission") -> "Assignment"
        text.contains("revision") || text.contains("review") -> "Revision"
        text.contains("project") || text.contains("lab") -> "Project"
        text.contains("plan") || text.contains("schedule") -> "Planning"
        else -> "Study action"
    }
}

private fun relativeDueLabel(dueAt: Long?): String {
    if (dueAt == null) return "No due date"
    val today = LocalDate.now()
    val dueDate = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayDelta = dueDate.toEpochDay() - today.toEpochDay()

    return when {
        dayDelta < 0 -> "Overdue by ${dayDelta.absoluteValue} day(s)"
        dayDelta == 0L -> "Due today"
        dayDelta == 1L -> "Due tomorrow"
        dayDelta <= 7L -> "Due in $dayDelta day(s)"
        else -> "Due ${formatEpochDate(dueAt)}"
    }
}
