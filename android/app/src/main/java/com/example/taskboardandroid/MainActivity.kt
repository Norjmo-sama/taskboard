package com.example.taskboardandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                TaskBoardScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBoardScreen(vm: TaskViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Board") },
                actions = {
                    if (state.uid != null) {
                        Text(
                            "${state.uid!!.take(6)}…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = vm::signOut) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.authChecked -> Centered { CircularProgressIndicator() }
                state.uid == null -> SignedOut(state.authError, vm::signIn)
                else -> Board(state, vm)
            }
        }
    }
}

@Composable
private fun SignedOut(error: String?, onSignIn: () -> Unit) = Centered {
    Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Text("You’re signed out", style = MaterialTheme.typography.titleLarge)
    Text("Tasks are only visible to signed-in users.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSignIn) { Text("Sign in anonymously") }
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Board(state: UiState, vm: TaskViewModel) {
    Column(Modifier.fillMaxSize()) {
        AddBar(state, vm)
        state.actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        when (val list = state.listState) {
            ListState.Loading -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Loading tasks…")
            }
            is ListState.Error -> Message(
                Icons.Filled.ErrorOutline, "Couldn’t load tasks", list.message,
                tint = MaterialTheme.colorScheme.error,
            )
            is ListState.Loaded ->
                if (list.tasks.isEmpty()) {
                    Message(Icons.Filled.Checklist, "No tasks yet",
                        "Add the first one above — it’ll show up on every device.")
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("taskList")) {
                        items(list.tasks, key = { it.id }) { task ->
                            TaskRow(task, isMine = task.createdBy == state.uid) { vm.toggle(task) }
                            HorizontalDivider()
                        }
                    }
                }
        }
    }
}

@Composable
private fun AddBar(state: UiState, vm: TaskViewModel) {
    val count = TitleValidator.trimmedLength(state.draft)
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = vm::onDraftChange,
                placeholder = { Text("What needs doing?") },
                singleLine = true,
                enabled = !state.saving,
                isError = state.formError != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addTask() }),
                modifier = Modifier.weight(1f).testTag("titleField"),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = vm::addTask, enabled = !state.saving, modifier = Modifier.testTag("addButton")) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Add")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                state.formError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count/${TitleValidator.MAX_LENGTH}",
                style = MaterialTheme.typography.bodySmall,
                color = if (count > TitleValidator.MAX_LENGTH) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (count > TitleValidator.MAX_LENGTH) FontWeight.Bold else null,
            )
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, isMine: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            val date = task.createdAt?.let {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
            } ?: "…"
            Text(
                if (isMine) "$date · you" else date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Message(
    icon: ImageVector, title: String, body: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Centered {
    Icon(icon, null, Modifier.size(48.dp), tint = tint)
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}