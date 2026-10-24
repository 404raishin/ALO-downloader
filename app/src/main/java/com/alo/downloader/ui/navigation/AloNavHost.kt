package com.alo.downloader.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alo.downloader.R
import com.alo.downloader.core.model.Platform
import com.alo.downloader.ui.accounts.AccountsScreen
import com.alo.downloader.ui.accounts.WebLoginScreen
import com.alo.downloader.ui.home.HomeScreen
import com.alo.downloader.ui.library.LibraryScreen
import com.alo.downloader.ui.library.openExternally
import com.alo.downloader.ui.settings.AboutScreen
import com.alo.downloader.ui.settings.AppearanceSettingsScreen
import com.alo.downloader.ui.settings.DownloadSettingsScreen
import com.alo.downloader.ui.settings.NetworkSettingsScreen
import com.alo.downloader.ui.settings.SettingsDestination
import com.alo.downloader.ui.settings.SettingsScreen
import com.alo.downloader.data.db.DownloadedFileEntity
import com.alo.downloader.ui.settings.EnginesScreen
import com.alo.downloader.ui.telegram.TelegramLoginScreen
import com.alo.downloader.ui.templates.TemplatesScreen
import com.alo.downloader.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val APPEARANCE = "settings/appearance"
    const val DOWNLOAD = "settings/download"
    const val NETWORK = "settings/network"
    const val ACCOUNTS = "settings/accounts"
    const val TEMPLATES = "settings/templates"
    const val ENGINES = "settings/engines"
    const val ABOUT = "settings/about"
    const val LOGIN = "login/{platform}"
    const val TELEGRAM = "settings/telegram"
    const val VIEWER = "viewer/{fileId}"

    fun viewer(fileId: Long) = "viewer/$fileId"

    fun login(platform: Platform) = "login/${platform.id}"
}

private data class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Outlined.Download, Icons.Filled.Download),
    TopLevel(Routes.LIBRARY, R.string.nav_library, Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AloNavHost(pendingLogin: String?, onPendingLoginConsumed: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val showBar = topLevel.any { tl -> current?.route == tl.route }

    LaunchedEffect(pendingLogin) {
        if (pendingLogin != null) {
            val platform = Platform.fromId(pendingLogin)
            nav.navigate(if (platform == Platform.TELEGRAM) Routes.TELEGRAM else Routes.login(platform))
            onPendingLoginConsumed()
        }
    }
    val login: (Platform) -> Unit = {
        nav.navigate(if (it == Platform.TELEGRAM) Routes.TELEGRAM else Routes.login(it))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = current?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(onLogin = login) }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onLogin = login,
                    onOpenItem = { item ->
                        val first = item.files.firstOrNull()
                        if (first != null && first.isViewable()) nav.navigate(Routes.viewer(first.id)) else openExternally(context, item)
                    },
                    onOpenFile = { file ->
                        if (file.isViewable()) nav.navigate(Routes.viewer(file.id))
                        else openExternally(context, file)
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onNavigate = { dest ->
                    nav.navigate(
                        when (dest) {
                            SettingsDestination.APPEARANCE -> Routes.APPEARANCE
                            SettingsDestination.DOWNLOAD -> Routes.DOWNLOAD
                            SettingsDestination.NETWORK -> Routes.NETWORK
                            SettingsDestination.ACCOUNTS -> Routes.ACCOUNTS
                            SettingsDestination.TEMPLATES -> Routes.TEMPLATES
                            SettingsDestination.ENGINES -> Routes.ENGINES
                            SettingsDestination.ABOUT -> Routes.ABOUT
                        },
                    )
                })
            }
            composable(Routes.APPEARANCE) { AppearanceSettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DOWNLOAD) {
                DownloadSettingsScreen(onBack = { nav.popBackStack() }, onOpenTemplates = { nav.navigate(Routes.TEMPLATES) })
            }
            composable(Routes.NETWORK) { NetworkSettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(onBack = { nav.popBackStack() }, onLogin = login, onTelegram = { nav.navigate(Routes.TELEGRAM) })
            }
            composable(Routes.TELEGRAM) { TelegramLoginScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ENGINES) { EnginesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.VIEWER, arguments = listOf(navArgument("fileId") { type = NavType.LongType })) { entry ->
                ViewerScreen(fileId = entry.arguments?.getLong("fileId") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(Routes.TEMPLATES) { TemplatesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LOGIN, arguments = listOf(navArgument("platform") { type = NavType.StringType })) { entry ->
                val platform = Platform.fromId(entry.arguments?.getString("platform"))
                WebLoginScreen(platform = platform, onDone = { nav.popBackStack() })
            }
        }
    }
}

private fun DownloadedFileEntity.isViewable() =
    mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType.startsWith("image/")

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
