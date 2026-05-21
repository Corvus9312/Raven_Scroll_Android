package com.corvus.bookreader.ui.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corvus.bookreader.R
import com.corvus.bookreader.data.model.DriveItem
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

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { vm.onSignedIn() }
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
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { item ->
                            DriveItemRow(
                                item = item,
                                onClick = {
                                    if (item.isFolder) vm.openFolder(item.id, item.name)
                                    else onOpenBook(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveItemRow(item: DriveItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Icon(
                if (item.isFolder) Icons.Default.Folder else Icons.Default.MenuBook,
                contentDescription = null,
                tint = if (item.isFolder) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (item.isFolder) Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}
