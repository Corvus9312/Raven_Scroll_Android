@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ravens.scroll.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ravens.scroll.R
import ravens.scroll.data.model.Book
import ravens.scroll.data.model.BookFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (uri: String) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val folderBooks by vm.folderBooks.collectAsState()
    var contextFolder by remember { mutableStateOf<BookFolder?>(null) }
    var contextBook by remember { mutableStateOf<Book?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.addFolder(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_folder))
                    }
                }
            )
        }
    ) { padding ->
        if (state.folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(stringResource(R.string.library_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Button(onClick = { folderPicker.launch(null) }) {
                        Text(stringResource(R.string.add_folder))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                state.folders.forEach { fwb ->
                    val books = folderBooks[fwb.folder.treeUri] ?: emptyList()
                    item(key = fwb.folder.treeUri) {
                        FolderItem(
                            folder = fwb.folder,
                            isExpanded = fwb.isExpanded,
                            bookCount = books.size,
                            readCount = books.count { it.percent > 0 },
                            onClick = {
                                vm.toggleFolder(fwb.folder)
                                if (!fwb.isExpanded) vm.refreshFolder(fwb.folder.treeUri)
                            },
                            onLongClick = { contextFolder = fwb.folder },
                        )
                    }
                    if (fwb.isExpanded) {
                        items(books, key = { it.uri }) { book ->
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

    contextFolder?.let { folder ->
        FolderContextMenu(
            folder = folder,
            onDismiss = { contextFolder = null },
            onRemove = { vm.removeFolder(folder); contextFolder = null },
            onResetProgress = { vm.resetFolderProgress(folder.treeUri); contextFolder = null },
        )
    }

    contextBook?.let { book ->
        BookContextMenu(
            book = book,
            onDismiss = { contextBook = null },
            onResetProgress = { vm.resetFileProgress(book.uri); contextBook = null },
        )
    }
}

@Composable
private fun FolderItem(
    folder: BookFolder,
    isExpanded: Boolean,
    bookCount: Int,
    readCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (bookCount > 0) Text("$readCount / $bookCount 本",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                book.percent >= 100 -> Text("✓ 完結",
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

@Composable
private fun FolderContextMenu(
    folder: BookFolder,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onResetProgress: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = {
            Column {
                TextButton(onClick = onResetProgress, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reset_folder_progress))
                }
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.remove_folder),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun BookContextMenu(
    book: Book,
    onDismiss: () -> Unit,
    onResetProgress: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            TextButton(onClick = onResetProgress, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reset_progress))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
