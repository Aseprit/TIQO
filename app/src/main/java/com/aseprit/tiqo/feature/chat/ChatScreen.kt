package com.aseprit.tiqo.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.R
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import com.aseprit.tiqo.core.designsystem.theme.tiqoAnimationsEnabled
import com.aseprit.tiqo.domain.model.ChatMessage
import com.aseprit.tiqo.domain.model.ChatRole

@Composable
fun ChatRoute(viewModel: ChatViewModel, onOpenModels: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unavailable = stringResource(R.string.ai_unavailable)
    ChatScreen(
        state = state,
        onSend = { viewModel.send(it, unavailable) },
        onStop = viewModel::stop,
        onUndo = viewModel::undo,
        onOpenModels = onOpenModels,
    )
}

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onUndo: (Long) -> Unit,
    onOpenModels: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val animationsEnabled = MaterialTheme.tiqoAnimationsEnabled
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            if (animationsEnabled) {
                listState.animateScrollToItem(state.messages.lastIndex)
            } else {
                listState.scrollToItem(state.messages.lastIndex)
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    statusLabel(state.status),
                    color = if (state.status == ChatAiStatus.Ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                stringResource(R.string.manage_models),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenModels).padding(8.dp),
            )
        }
        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.fillMaxWidth().padding(20.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.chat_empty_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.manage_models),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onOpenModels).padding(top = 6.dp),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        canUndo = message.id in state.undoTokens,
                        onUndo = { onUndo(message.id) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 5,
                enabled = !state.isGenerating,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (state.isGenerating) onStop() else {
                        onSend(input)
                        input = ""
                    }
                },
                modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                enabled = state.isGenerating || input.isNotBlank(),
            ) {
                Icon(
                    if (state.isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                    stringResource(if (state.isGenerating) R.string.stop else R.string.send),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, canUndo: Boolean, onUndo: () -> Unit) {
    val isUser = message.role == ChatRole.User
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (message.role == ChatRole.Tool) 0.72f else 0.84f)
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (isUser) 22.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 22.dp,
                    ),
                )
                .background(
                    when (message.role) {
                        ChatRole.User -> MaterialTheme.colorScheme.primary
                        ChatRole.Tool -> MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
                        ChatRole.Assistant -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                message.content,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (message.role == ChatRole.Tool) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
            )
            if (canUndo) {
                TextButton(onClick = onUndo) { Text("COFNIJ") }
            }
        }
    }
}

private fun statusLabel(status: ChatAiStatus): String = when (status) {
    ChatAiStatus.Unavailable -> "AI niedostępne"
    ChatAiStatus.Ready -> "AI gotowe"
    ChatAiStatus.Thinking -> "Interpretuję…"
    ChatAiStatus.Executing -> "Wykonuję…"
    ChatAiStatus.Generating -> "Generuję…"
    ChatAiStatus.Error -> "Błąd AI"
}
