package com.sainiksahayak.app.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sainiksahayak.app.llm.ModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onPickModel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    Column {
                        Text("Sainik Sahayak", style = MaterialTheme.typography.titleMedium)
                        Text(
                            subtitle(state.model),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNewChat, enabled = state.messages.isNotEmpty()) {
                        Text("New chat", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
            )
        },
        bottomBar = {
            if (state.model is ModelState.Ready) {
                Column(
                    Modifier
                        .imePadding()
                        .navigationBarsPadding(),
                ) {
                    InputBar(
                        busy = state.busy,
                        status = state.status,
                        onSend = onSend,
                        onStop = onStop,
                    )
                    Disclaimer()
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val model = state.model) {
                is ModelState.NoModel -> ModelSetup(
                    message = "No model loaded yet.",
                    canRetry = false,
                    onPickModel = onPickModel,
                )

                is ModelState.Failed -> ModelSetup(
                    message = model.message,
                    canRetry = true,
                    onPickModel = onPickModel,
                )

                is ModelState.Importing -> ProgressScreen(
                    title = "Copying model…",
                    detail = "${(model.progress * 100).toInt()}% — keep the app open",
                    progress = model.progress,
                )

                is ModelState.Loading -> ProgressScreen(
                    title = "Loading model…",
                    detail = "Reading the GGUF into RAM. This takes a few seconds the first time.",
                    progress = model.progress.takeIf { it > 0f },
                )

                is ModelState.Ready ->
                    if (state.messages.isEmpty()) {
                        EmptyState()
                    } else {
                        MessageList(state.messages)
                    }
            }
        }
    }
}

private fun subtitle(model: ModelState): String = when (model) {
    is ModelState.Ready -> "Offline • ${model.name}"
    is ModelState.Importing -> "Importing model…"
    is ModelState.Loading -> "Loading model…"
    is ModelState.Failed -> "Model error"
    ModelState.NoModel -> "Offline • no model loaded"
}

@Composable
private fun MessageList(messages: List<ChatMessage>) {
    val listState = rememberLazyListState()
    val lastTextLength = messages.lastOrNull()?.text?.length ?: 0

    // Follow the streamed answer as it grows.
    LaunchedEffect(messages.size, lastTextLength) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.User
    val container = when {
        isUser -> MaterialTheme.colorScheme.primary
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                when {
                    message.text.isNotEmpty() -> Text(
                        text = message.text,
                        color = content,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    message.streaming -> Text(
                        text = "…",
                        color = content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (message.sources.isNotEmpty()) {
                    SourcesSection(message.sources, content)
                }
            }
        }
    }
}

@Composable
private fun SourcesSection(sources: List<SourceRef>, contentColor: androidx.compose.ui.graphics.Color) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        text = "Sources",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = contentColor.copy(alpha = 0.7f),
    )
    Spacer(Modifier.height(4.dp))
    for (source in sources) {
        Text(
            text = "${source.index}. ${source.label}",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(vertical = 1.dp),
        )
    }
}

@Composable
private fun InputBar(
    busy: Boolean,
    status: String?,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (draft.isNotBlank() && !busy) {
            onSend(draft)
            draft = ""
        }
    }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about medical, weapons, field craft…") },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            if (busy) {
                OutlinedButton(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = { submit() }, enabled = draft.isNotBlank()) { Text("Send") }
            }
        }
    }
}

@Composable
private fun Disclaimer() {
    Text(
        text = "Informational only — not a substitute for official Army manuals or medical professionals.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Jai Hind.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ask about first aid, weapons, field craft, tactics, communications or administration. " +
                "Answers come only from the knowledge base stored on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ModelSetup(message: String, canRetry: Boolean, onPickModel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Load a model to start",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "This app runs Llama 3.2 entirely on the phone, so it needs a GGUF file. " +
                "Copy llama-3.2-3b-instruct-q4_k_m.gguf to your phone, then pick it here. " +
                "It is copied into app storage once (about 2 GB).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (canRetry) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPickModel, modifier = Modifier.widthIn(min = 200.dp)) {
            Text(if (canRetry) "Choose another .gguf" else "Choose .gguf file")
        }
    }
}

@Composable
private fun ProgressScreen(title: String, detail: String, progress: Float?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (progress == null) {
            CircularProgressIndicator()
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
