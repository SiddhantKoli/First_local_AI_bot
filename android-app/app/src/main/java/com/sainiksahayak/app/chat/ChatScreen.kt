package com.sainiksahayak.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sainiksahayak.app.R
import com.sainiksahayak.app.llm.ModelState
import com.sainiksahayak.app.ui.AccentGreen
import com.sainiksahayak.app.ui.Bg
import com.sainiksahayak.app.ui.CardBg
import com.sainiksahayak.app.ui.InputBorder
import com.sainiksahayak.app.ui.SurfaceGlass
import com.sainiksahayak.app.ui.TextSecondary

private data class QuickCategory(val label: String, val hint: String, val prompt: String)

private val QuickCategories = listOf(
    QuickCategory("Procedures", "SOPs & drills", "What are the standard operating procedures for patrols?"),
    QuickCategory("Weapons", "Specs & usage", "Explain the specifications and usage of the AK-203 rifle."),
    QuickCategory("Tactics", "Battle craft", "What are the principles of fire and movement in a section attack?"),
    QuickCategory("Equipment", "Assets & vehicles", "What are the capabilities of the BMP-2 infantry combat vehicle?"),
    QuickCategory("Medical", "Combat first aid", "How do I treat a casualty with a gunshot wound to the chest in the field?"),
    QuickCategory("Intel", "Recon ops", "What is the role of an Observation Post in gathering intelligence?"),
)

@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onPickModel: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.site_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xB30B1118), Color(0xF20B1118)),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (state.model is ModelState.Ready && state.messages.isNotEmpty()) {
                TopBar(
                    subtitle = subtitle(state.model),
                    showNewChat = true,
                    onNewChat = onNewChat,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            HeroEmptyState(onQuickAsk = onSend)
                        } else {
                            MessageList(state.messages)
                        }
                }
            }

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
                    StatusFooter()
                }
            }
        }
    }
}

@Composable
private fun TopBar(subtitle: String, showNewChat: Boolean, onNewChat: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "SAINIK SAHAYAK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showNewChat) {
            TextButton(onClick = onNewChat) {
                Text("New chat", color = AccentGreen)
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

    LaunchedEffect(messages.size, lastTextLength) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (message.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else if (isUser) {
                SurfaceGlass
            } else {
                CardBg
            },
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp,
            ),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(
                    width = 1.dp,
                    color = if (message.isError) Color.Transparent else Color(0x14FFFFFF),
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                when {
                    message.text.isNotEmpty() -> Text(
                        text = message.text,
                        color = if (message.isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            Color.White
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    message.streaming -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentGreen,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Writing answer…",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (message.sources.isNotEmpty() && !message.streaming) {
                    SourcesSection(message.sources)
                }
            }
        }
    }
}

@Composable
private fun SourcesSection(sources: List<SourceRef>) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = Color(0x1AFFFFFF),
    )
    Text(
        text = "SOURCES",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = AccentGreen,
        letterSpacing = 0.8.sp,
    )
    Spacer(Modifier.height(6.dp))
    for (source in sources) {
        Text(
            text = "${source.index}. ${source.label}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 1.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }

        Surface(
            color = Color(0xB30E151F),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, InputBorder.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(AccentGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("★", color = AccentGreen, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(AccentGreen),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text(
                                        "Ask about procedures, weapons, tactics…",
                                        color = Color(0xFF5B6A7A),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        listOf("Procedures", "Weapons", "Tactics", "Medical").forEach { tag ->
                            TagChip(tag) { draft = "$tag: " }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (busy) {
                        TextButton(onClick = onStop) {
                            Text("Stop", color = AccentGreen)
                        }
                    } else {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(AccentGreen, RoundedCornerShape(10.dp))
                                .clickable(enabled = draft.isNotBlank()) { submit() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("→", color = Bg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        Text(
            text = "Informational only — not a substitute for official Army manuals or medical professionals.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TagChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .background(Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun StatusFooter() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC0B1118))
            .border(width = 0.dp, color = Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FooterCell("SECURE", "On-device only")
        FooterCell("CLASSIFICATION", "RESTRICTED")
        FooterCell("AI MODEL", "Llama 3.2 Local")
    }
}

@Composable
private fun FooterCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            letterSpacing = 0.8.sp,
            fontSize = 9.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun HeroEmptyState(onQuickAsk: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "★  AI ASSISTANT FOR INDIAN ARMY PERSONNEL",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                .background(Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "SAINIK SAHAYAK",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "(सैनिक सहायक)",
            style = MaterialTheme.typography.headlineSmall,
            color = AccentGreen,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your intelligent companion for military knowledge & operations",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "QUICK ACCESS",
            style = MaterialTheme.typography.labelMedium,
            color = AccentGreen,
            letterSpacing = 1.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )
        QuickAccessGrid(onQuickAsk)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QuickAccessGrid(onQuickAsk: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickCategories.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { cat ->
                    QuickCard(
                        category = cat,
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAsk(cat.prompt) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickCard(
    category: QuickCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0x14FFFFFF), Color(0x05FFFFFF)),
                ),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(category.label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(category.hint, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("→", color = TextSecondary, fontSize = 12.sp)
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
            text = "SAINIK SAHAYAK",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "(सैनिक सहायक)",
            style = MaterialTheme.typography.headlineSmall,
            color = AccentGreen,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Load a model to start",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "This app runs Llama 3.2 entirely on the phone, so it needs a GGUF file. " +
                "Copy Llama-3.2-3B-Instruct-Q4_K_M.gguf to your phone, then pick it here. " +
                "It is copied into app storage once (about 2 GB).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
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
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPickModel,
            modifier = Modifier.widthIn(min = 200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = Bg,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(16.dp))
        if (progress == null) {
            CircularProgressIndicator(color = AccentGreen)
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.8f),
                color = AccentGreen,
                trackColor = AccentGreen.copy(alpha = 0.2f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
