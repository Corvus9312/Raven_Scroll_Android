@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ravens.scroll.ui.drive

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import ravens.scroll.R
import ravens.scroll.data.model.DriveItem
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.drive.DriveScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    onOpenBook: (fileId: String) -> Unit,
    vm: DriveViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var contextFolder by remember { mutableStateOf<DriveItem?>(null) }

    // 每次畫面重新顯示（從閱讀器返回、切 tab）就同步一次進度，確保與 VS Code 一致
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && vm.state.value.isSignedIn) {
                vm.refreshProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { vm.onSignedIn() }
                .addOnFailureListener { e ->
                    android.util.Log.e("SignIn", "getSignedInAccount failed: ${e.message}")
                }
        } else {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                android.util.Log.e("SignIn", "ApiException statusCode=${e.statusCode} msg=${e.message}")
            }
            android.util.Log.e("SignIn", "resultCode=${result.resultCode}")
        }
    }

    fun startSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_READONLY),
                com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA),
            )
            .build()
        signInLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val path = state.stack.joinToString(" › ") { it.second }
                        .ifEmpty { stringResource(R.string.nav_drive) }
                    Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (state.stack.size > 1) {
                        IconButton(onClick = { vm.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (state.stack.size > 1) {
                        IconButton(onClick = { vm.navigateRoot() }) {
                            Icon(Icons.Default.Home, contentDescription = stringResource(R.string.drive_root))
                        }
                    }
                    if (state.isSignedIn) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { vm.signOut() }) {
                            Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.sign_out))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.isSignedIn -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(stringResource(R.string.drive_sign_in_prompt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = ::startSignIn) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_in_google))
                        }
                    }
                }
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> {
                    Column(Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.retry)) }
                    }
                }
                state.items.isEmpty() -> {
                    Text(stringResource(R.string.drive_empty),
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        state.items.forEach { item ->
                            if (item.isFolder) {
                                val filesInFolder = state.fileParentMap
                                    .filter { (_, pId) -> pId == item.id }.keys
                                val total = filesInFolder.size
                                val readCount = filesInFolder.count {
                                    (state.progressMap[it]?.second ?: 0) > 0
                                }
                                val completedCount = filesInFolder.count {
                                    (state.progressMap[it]?.second ?: 0) >= 95
                                }
                                val isExpanded = item.id in state.expandedFolderIds

                                item(key = item.id) {
                                    DriveFolderItem(
                                        item = item,
                                        isExpanded = isExpanded,
                                        bookCount = total,
                                        readCount = readCount,
                                        completedCount = completedCount,
                                        onClick = { vm.toggleFolder(item.id) },
                                        onLongClick = { contextFolder = item },
                                    )
                                }

                                if (isExpanded) {
                                    val children = state.folderFiles[item.id]
                                    if (children == null) {
                                        item(key = "loading_${item.id}") {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 56.dp)
                                                    .height(2.dp),
                                            )
                                        }
                                    } else {
                                        items(children, key = { "${item.id}_${it.id}" }) { child ->
                                            if (child.isFolder) {
                                                // 子資料夾：點擊進入
                                                DriveFolderItem(
                                                    item = child,
                                                    isExpanded = false,
                                                    bookCount = 0,
                                                    readCount = 0,
                                                    completedCount = 0,
                                                    onClick = { vm.openFolder(child.id, child.name) },
                                                    onLongClick = { contextFolder = child },
                                                )
                                            } else {
                                                val pct = state.progressMap[child.id]?.second ?: 0
                                                DriveBookItem(
                                                    item = child,
                                                    percent = pct,
                                                    onClick = { onOpenBook(child.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val pct = state.progressMap[item.id]?.second ?: 0
                                item(key = item.id) {
                                    DriveBookItem(
                                        item = item,
                                        percent = pct,
                                        onClick = { onOpenBook(item.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    contextFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { contextFolder = null },
            title = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                TextButton(
                    onClick = {
                        vm.enterAndPin(folder.id, folder.name)
                        contextFolder = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("進入並設為預設目錄")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextFolder = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DriveFolderItem(
    item: DriveItem,
    isExpanded: Boolean,
    bookCount: Int,
    readCount: Int,
    completedCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (bookCount > 0) {
                val label = when {
                    completedCount >= bookCount -> "✓ 全部完結"
                    else -> "$readCount / $bookCount 本"
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completedCount >= bookCount) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
    HorizontalDivider()
}

@Composable
private fun DriveBookItem(
    item: DriveItem,
    percent: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (percent > 0) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(0.6f).height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        leadingContent = {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        },
        trailingContent = {
            when {
                percent >= 95 -> Text("✓ 完結",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                percent > 0 -> Text("$percent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = {}),
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
