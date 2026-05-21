package com.corvus.bookreader.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.corvus.bookreader.R
import com.corvus.bookreader.ui.drive.DriveScreen
import com.corvus.bookreader.ui.library.LibraryScreen
import com.corvus.bookreader.ui.reader.ReaderScreen
import com.corvus.bookreader.ui.recent.RecentScreen

@Composable
fun AppNavigation(openFileUri: String? = null) {
    val navController = rememberNavController()
    val currentBack by navController.currentBackStackEntryAsState()
    val currentDest = currentBack?.destination

    val isReaderVisible = currentDest?.route?.startsWith("reader/") == true

    Scaffold(
        bottomBar = {
            if (!isReaderVisible) {
                NavigationBar {
                    listOf(
                        Triple(Screen.Library, Icons.Default.Folder, R.string.nav_library),
                        Triple(Screen.Recent, Icons.Default.History, R.string.nav_recent),
                        Triple(Screen.Drive, Icons.Default.Cloud, R.string.nav_drive),
                    ).forEach { (screen, icon, labelRes) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(labelRes)) },
                            selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onOpenBook = { uri -> navController.navigate(Screen.readerRoute(uri, false)) }
                )
            }
            composable(Screen.Recent.route) {
                RecentScreen(
                    onOpenBook = { uri -> navController.navigate(Screen.readerRoute(uri, false)) }
                )
            }
            composable(Screen.Drive.route) {
                DriveScreen(
                    onOpenBook = { fileId -> navController.navigate(Screen.readerRoute(fileId, true)) }
                )
            }
            composable("reader/{uri}/{isDrive}") { back ->
                val encodedUri = back.arguments?.getString("uri") ?: ""
                val isDrive = back.arguments?.getString("isDrive")?.toBoolean() ?: false
                val uri = Screen.decodeUri(encodedUri)
                ReaderScreen(
                    uri = uri,
                    isDrive = isDrive,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // Open file passed via Intent
    LaunchedEffect(openFileUri) {
        if (!openFileUri.isNullOrEmpty()) {
            navController.navigate(Screen.readerRoute(openFileUri, false))
        }
    }
}
