package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.ui.MainViewModel

/** Text shared from another app: which audiobook does it belong to? */
@Composable
fun SharedTextDialog(vm: MainViewModel, shared: MainViewModel.SharedText) {
    val books by vm.allBooks.collectAsStateWithLifecycle()
    val last by vm.lastBook.collectAsStateWithLifecycle()
    val ordered = listOfNotNull(last) + books.filter { it.id != last?.id }
    AlertDialog(
        onDismissRequest = { vm.assignSharedText(null) },
        title = { Text("Buchtext übernehmen") },
        text = {
            Column {
                Text(
                    "${shared.text.text.length / 1000} Tsd. Zeichen, ${shared.text.chapters.size} Kapitel erkannt. Zu welchem Hörbuch gehört der Text?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    if (ordered.isEmpty()) Text("Noch keine Hörbücher in der Bibliothek.", style = MaterialTheme.typography.bodySmall)
                    ordered.forEach { book ->
                        Text(
                            book.title + (book.author?.let { " – $it" } ?: ""),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { vm.assignSharedText(book.id) }.padding(vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.assignSharedText(null) }) { Text("Abbrechen") } },
    )
}
