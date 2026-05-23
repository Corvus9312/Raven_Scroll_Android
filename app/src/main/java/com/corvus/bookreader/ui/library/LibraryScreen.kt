@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ravens.scroll.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import ravens.scroll.R
import ravens.scroll.data.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (uri: String) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var contextBook by remember { mutableStateOf<Book?>(null) }
    var contextFolderPath by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_library)) })
        }
    ) { padding ->
        if (state.subFolders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(
                        "尚未下載任何書籍\n請前往 Google Drive 下載書籍",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                state.subFolders.forEach { folder ->
                    item(key = folder.path) {
                        FolderItem(
                            name = folder.name,
                            isExpanded = folder.isExpanded,
                            bookCount = folder.books.size,
                            readCount = folder.books.count { it.percent > 0 },
                            completedCount = folder.books.count { it.percent >= 95 },
                            onClick = { vm.toggleFolder(folder.path) },
                            onLongClick = { contextFolderPath = folder.path },
                        )
                    }
                    if (folder.isExpanded) {
                        items(folder.books, key = { it.uri }) { book ->
                            BookItem(
                                book = book,
                                onClick = { onOpenBook(book.uri) },
                                onLongClick = { contextBook = book },
                            )
                        }
                    }
                }
            }
        }
    }

    contextFolderPath?.let { path ->
        AlertDialog(
            onDismissRequest = { contextFolderPath = null },
            title = { Text(state.subFolders.firstOrNull { it.path == path }?.name ?: "") },
            text = {
                TextButton(
                    onClick = { vm.resetFolderProgress(path); contextFolderPath = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.reset_folder_progress))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextFolderPath = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    contextBook?.let { book ->
        AlertDialog(
            onDismissRequest = { contextBook = null },
            title = { Text(book.title) },
            text = {
                TextButton(
                    onClick = { vm.resetFileProgress(book.uri); contextBook = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.reset_progress))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextBook = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FolderItem(
    name: String,
    isExpanded: Boolean,
    bookCount: Int,
    readCount: Int,
    completedCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (bookCount > 0) {
                val label = when {
                    completedCount >= bookCount -> "✓ 全部完結"
                    else -> "$readCount / $bookCount 本"
                }
                Text(label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completedCount >= bookCount) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
            Icon(if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null)
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
    HorizontalDivider()
}

@Composable
private fun BookItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (book.percent > 0) {
                LinearProgressIndicator(
                    progress = { book.percent / 100f },
                    modifier = Modifier.fillMaxWidth(0.6f).height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        leadingContent = {
            Icon(Icons.Default.MenuBook, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp))
        },
        trailingContent = {
            when {
                book.percent >= 95 -> Text("✓ 完結",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                book.percent > 0 -> Text("${book.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
