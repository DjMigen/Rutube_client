package ru.rutubeclient.tv

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.rutubeclient.tv.ui.TvTheme
import ru.rutubeclient.tv.ui.screens.TvChannelScreen
import ru.rutubeclient.tv.ui.screens.TvChannelViewModel
import ru.rutubeclient.tv.ui.screens.TvHomeScreen
import ru.rutubeclient.tv.ui.screens.TvHomeViewModel
import ru.rutubeclient.tv.ui.screens.TvLibraryScreen
import ru.rutubeclient.tv.ui.screens.TvLibraryViewModel
import ru.rutubeclient.tv.ui.screens.TvSearchScreen
import ru.rutubeclient.tv.ui.screens.TvSearchViewModel
import ru.rutubeclient.tv.ui.screens.TvSubscriptionsScreen
import ru.rutubeclient.tv.ui.screens.TvSubscriptionsViewModel
import ru.rutubeclient.tv.ui.screens.TvVideoScreen
import ru.rutubeclient.tv.ui.screens.TvVideoViewModel
import ru.rutubeclient.tv.ui.screens.catalog.TvCatalogScreen
import ru.rutubeclient.tv.ui.screens.catalog.TvCatalogViewModel
import ru.rutubeclient.tv.ui.screens.catalog.TvShowScreen
import ru.rutubeclient.tv.ui.screens.catalog.TvShowViewModel

object TvRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIBRARY = "library"
    const val CATALOG = "catalog"
    const val TV_SHOW = "tvshow/{tvId}"
    const val VIDEO = "video/{videoId}?fromStart={fromStart}&next={next}"
    const val CHANNEL = "channel/{authorId}?name={name}"

    fun video(id: String, fromStart: Boolean = false, next: String? = null): String {
        val base = "video/$id?fromStart=$fromStart"
        return if (next.isNullOrEmpty()) base else "$base&next=$next"
    }
    fun tvShow(id: Int) = "tvshow/$id"
    fun channel(authorId: Long, name: String) =
        "channel/$authorId?name=${android.net.Uri.encode(name)}"
}

class MainActivity : ComponentActivity() {

    private var voiceSearchQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleVoiceSearch(intent)
        setContent {
            TvTheme {
                TvNavHost(voiceSearchQuery)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceSearch(intent)
    }

    private fun handleVoiceSearch(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEARCH) {
            voiceSearchQuery = intent.getStringExtra(SearchManager.QUERY)
        }
    }
}

private data class TopLevel(val route: String, val label: String)

private val topLevel = listOf(
    TopLevel(TvRoutes.HOME, "Главная"),
    TopLevel(TvRoutes.SEARCH, "Поиск"),
    TopLevel(TvRoutes.SUBSCRIPTIONS, "Подписки"),
    TopLevel(TvRoutes.LIBRARY, "Библиотека"),
    TopLevel(TvRoutes.CATALOG, "Каталог"),
)

@Composable
fun TvNavHost(voiceSearchQuery: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in topLevel.map { it.route }

    Row(Modifier.fillMaxSize()) {
        // Left navigation rail (always visible on top-level screens).
        if (isTopLevel) {
            Column(Modifier.width(200.dp).padding(24.dp)) {
                androidx.tv.material3.Text(
                    "RuTube",
                    style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
                    color = androidx.tv.material3.MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(32.dp))
                topLevel.forEach { dest ->
                    val selected = currentRoute == dest.route
                    androidx.tv.material3.Button(
                        onClick = {
                            navController.navigate(dest.route) {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        androidx.tv.material3.Text(
                            if (selected) "▸ ${dest.label}" else dest.label
                        )
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = TvRoutes.HOME,
            modifier = Modifier.weight(1f),
        ) {
            composable(TvRoutes.HOME) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvHomeViewModel = viewModel(factory = tvVmFactory {
                    TvHomeViewModel(container.api, container.repository)
                })
                TvHomeScreen(
                    vm,
                    onVideoClick = { navController.navigate(TvRoutes.video(it)) },
                    onWatchFromStart = { navController.navigate(TvRoutes.video(it, fromStart = true)) },
                )
            }
            composable(TvRoutes.SEARCH) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvSearchViewModel = viewModel(factory = tvVmFactory {
                    TvSearchViewModel(container.api)
                })
                LaunchedEffect(voiceSearchQuery) {
                    voiceSearchQuery?.let { vm.setQuery(it) }
                }
                TvSearchScreen(vm, onVideoClick = { navController.navigate(TvRoutes.video(it)) })
            }
            composable(TvRoutes.SUBSCRIPTIONS) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvSubscriptionsViewModel = viewModel(factory = tvVmFactory {
                    TvSubscriptionsViewModel(container.repository)
                })
                TvSubscriptionsScreen(
                    vm,
                    onChannelClick = { id, name -> navController.navigate(TvRoutes.channel(id, name)) },
                )
            }
            composable(TvRoutes.LIBRARY) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvLibraryViewModel = viewModel(factory = tvVmFactory {
                    TvLibraryViewModel(container.repository)
                })
                TvLibraryScreen(
                    vm,
                    onVideoClick = { navController.navigate(TvRoutes.video(it)) },
                )
            }
            composable(TvRoutes.CATALOG) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvCatalogViewModel = viewModel(factory = tvVmFactory {
                    TvCatalogViewModel(container.api)
                })
                TvCatalogScreen(
                    vm,
                    onVideoClick = { navController.navigate(TvRoutes.video(it)) },
                    onTvShowClick = { navController.navigate(TvRoutes.tvShow(it)) },
                )
            }
            composable(
                TvRoutes.TV_SHOW,
                arguments = listOf(navArgument("tvId") { type = NavType.IntType }),
            ) { entry ->
                val tvId = entry.arguments?.getInt("tvId") ?: return@composable
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvShowViewModel = viewModel(
                    key = "tvshow-$tvId",
                    factory = tvVmFactory {
                        TvShowViewModel(tvId, container.api)
                    },
                )
                TvShowScreen(
                    viewModel = vm,
                    onVideoClick = { videoId, nextId ->
                        navController.navigate(TvRoutes.video(videoId, next = nextId))
                    },
                )
            }
            composable(
                TvRoutes.VIDEO,
                arguments = listOf(
                    navArgument("videoId") { type = NavType.StringType },
                    navArgument("fromStart") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("next") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId") ?: return@composable
                val fromStart = entry.arguments?.getBoolean("fromStart") ?: false
                val next = entry.arguments?.getString("next")
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvVideoViewModel = viewModel(
                    key = "video-$videoId-$fromStart-$next",
                    factory = tvVmFactory {
                        TvVideoViewModel(
                            videoId,
                            container.api,
                            container.repository,
                            startFromBeginning = fromStart,
                            nextVideoId = next,
                        )
                    },
                )
                TvVideoScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(TvRoutes.video(it)) },
                )
            }
            composable(
                TvRoutes.CHANNEL,
                arguments = listOf(
                    navArgument("authorId") { type = NavType.LongType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { entry ->
                val authorId = entry.arguments?.getLong("authorId") ?: return@composable
                val name = entry.arguments?.getString("name").orEmpty()
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvChannelViewModel = viewModel(
                    key = "channel-$authorId",
                    factory = tvVmFactory {
                        TvChannelViewModel(authorId, name, container.api, container.repository)
                    },
                )
                TvChannelScreen(
                    viewModel = vm,
                    onVideoClick = { navController.navigate(TvRoutes.video(it)) },
                )
            }
        }
    }
}

private inline fun <reified VM : androidx.lifecycle.ViewModel> tvVmFactory(
    crossinline create: () -> VM,
): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VM::class.java))
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    }
