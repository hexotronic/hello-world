@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.core.ScenePrompt
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroToggle
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.ui.theme.PixelFont

private val QUESTIONS = listOf("Was ist bisher passiert?", "Wer ist wer?", "Wo spielt das gerade?", "Warum ist das passiert?")
private val COMMANDS = listOf("Nächstes Kapitel", "30 Sekunden zurück", "Schlaf-Timer 20 Minuten", "Musik aus")

/**
 * "Frag das Buch": a side window for questions about the book (answered on the phone from what was
 * transcribed, by default only up to the listening position) and for commands the player carries out.
 */
@Composable
fun AssistantSheet(vm: MainViewModel) {
    val open by vm.chatOpen.collectAsStateWithLifecycle()
    if (!open) return
    val chat by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()
    val spoilerFree by vm.spoilerFree.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val settings by vm.musicSettings.collectAsStateWithLifecycle()
    val changes by vm.models.changes.collectAsStateWithLifecycle()
    val hasModel = remember(settings.llmModel, changes) { vm.models.isInstalled(settings.llmModel) }
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(chat.size, busy) { if (chat.isNotEmpty()) list.animateScrollToItem(chat.size - 1) }

    fun send(q: String = text) {
        if (q.isBlank() || busy) return
        vm.ask(q)
        text = ""
    }

    ModalBottomSheet(onDismissRequest = { vm.closeChat() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Frag das Buch", fontFamily = PixelFont, fontSize = 14.sp)
                    Text(
                        (np.book?.title ?: "Kein Hörbuch") + " · bei " + ScenePrompt.clock(np.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    vm.closeChat()
                    vm.openScreen("prompts")
                }) { Text("Anweisungen") }
                if (chat.isNotEmpty()) TextButton(onClick = { vm.clearChat() }) { Text("Leeren") }
            }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keine Spoiler", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (spoilerFree) "Antworten nur aus dem, was du schon gehört hast" else "Auch spätere, schon analysierte Teile werden benutzt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RetroToggle(spoilerFree, { vm.spoilerFree.value = it })
            }
            if (!hasModel) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .block(MaterialTheme.colorScheme.tertiaryContainer, shadow = 2.dp)
                        .padding(10.dp),
                ) {
                    Text(
                        "Für Fragen braucht es ein Sprachmodell auf dem Handy. Befehle wie „Kapitel 3“, „zu 1:20:00“ oder " +
                            "„Schlaf-Timer 20 Minuten“ klappen auch ohne.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    RetroButton(onClick = {
                        vm.closeChat()
                        vm.openScreen("music_ai")
                    }, style = RetroButtonStyle.SECONDARY) { Text("Sprachmodell wählen") }
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 380.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (chat.isEmpty()) {
                    item {
                        Text(
                            "Frag nach Figuren, Orten oder was zuletzt geschah – oder sag, was der Player tun soll " +
                                "(„Bring mich zur Stelle mit dem Drachen“, „etwas schneller“).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(chat) { e -> ChatBubbles(e, busy && e === chat.last()) }
            }

            if (chat.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (QUESTIONS + COMMANDS).forEach { s -> AssistChip(onClick = { send(s) }, label = { Text(s) }, enabled = !busy) }
                }
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Frage oder Befehl …") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { send() }, enabled = !busy && text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
                }
            }
        }
    }
}

@Composable
private fun ChatBubbles(e: MainViewModel.ChatEntry, thinking: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                e.question,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .block(MaterialTheme.colorScheme.primaryContainer, shadow = 2.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .block(MaterialTheme.colorScheme.surfaceContainerHigh, shadow = 2.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            when {
                e.error != null -> Text(e.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                e.answer == null && thinking -> {
                    Text("denkt nach …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                else -> Text(e.answer.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
            e.done?.let {
                Spacer(Modifier.height(4.dp))
                Text("→ $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
