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
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.rutubeclient.core.player.PlaybackSession
import ru.rutubeclient.core.player.StreamResolver
import ru.rutubeclient.core.presentation.CatalogViewModel
import ru.rutubeclient.core.presentation.ChannelViewModel
import ru.rutubeclient.core.presentation.HomeViewModel
import ru.rutubeclient.core.presentation.Routes
import ru.rutubeclient.core.presentation.TvShowViewModel
import ru.rutubeclient.core.presentation.vmFactory
import ru.rutubeclient.tv.ui.TvTheme
import ru.rutubeclient.tv.ui.screens.TvChannelScreen
import ru.rutubeclient.tv.ui.screens.TvHomeScreen
import ru.rutubeclient.tv.ui.screens.TvLibraryScreen
import ru.rutubeclient.tv.ui.screens.TvLibraryViewModel
import ru.rutubeclient.tv.ui.screens.TvSearchScreen
import ru.rutubeclient.tv.ui.screens.TvSearchViewModel
import ru.rutubeclient.tv.ui.screens.TvSubscriptionsScreen
import ru.rutubeclient.tv.ui.screens.TvSubscriptionsViewModel
import ru.rutubeclient.tv.ui.screens.TvVideoScreen
import ru.rutubeclient.tv.ui.screens.TvVideoViewModel
import ru.rutubeclient.tv.ui.screens.catalog.TvCatalogScreen
import ru.rutubeclient.tv.ui.screens.catalog.TvShowScreen

class MainActivity : ComponentActivity() {

    private var voiceSearchQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFFD9D9D9.toInt()))
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
    TopLevel(Routes.HOME, "Главная"),
    TopLevel(Routes.SEARCH, "Поиск"),
    TopLevel(Routes.SUBSCRIPTIONS, "Подписки"),
    TopLevel(Routes.LIBRARY, "Библиотека"),
    TopLevel(Routes.CATALOG, "Каталог"),
)

@Composable
fun TvNavHost(voiceSearchQuery: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in topLevel.map { it.route }

    Row(Modifier.fillMaxSize().background(androidx.tv.material3.MaterialTheme.colorScheme.background)) {
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
            startDestination = Routes.HOME,
            modifier = Modifier.weight(1f),
        ) {
            composable(Routes.HOME) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: HomeViewModel = viewModel(factory = vmFactory {
                    HomeViewModel(
                        container.api,
                        container.repository,
                        categoryLimit = 5,
                        alwaysRecommendations = true,
                    )
                })
                TvHomeScreen(
                    vm,
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                    onWatchFromStart = { navController.navigate(Routes.video(it, fromStart = true)) },
                )
            }
            composable(Routes.SEARCH) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvSearchViewModel = viewModel(factory = vmFactory {
                    TvSearchViewModel(container.api)
                })
                LaunchedEffect(voiceSearchQuery) {
                    voiceSearchQuery?.let { vm.setQuery(it) }
                }
                TvSearchScreen(vm, onVideoClick = { navController.navigate(Routes.video(it)) })
            }
            composable(Routes.SUBSCRIPTIONS) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvSubscriptionsViewModel = viewModel(factory = vmFactory {
                    TvSubscriptionsViewModel(container.repository)
                })
                TvSubscriptionsScreen(
                    vm,
                    onChannelClick = { id, name -> navController.navigate(Routes.channel(id, name)) },
                )
            }
            composable(Routes.LIBRARY) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvLibraryViewModel = viewModel(factory = vmFactory {
                    TvLibraryViewModel(container.repository)
                })
                TvLibraryScreen(
                    vm,
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                )
            }
            composable(Routes.CATALOG) {
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: CatalogViewModel = viewModel(factory = vmFactory {
                    CatalogViewModel(container.api)
                })
                TvCatalogScreen(
                    vm,
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                    onTvShowClick = { navController.navigate(Routes.tvShow(it)) },
                )
            }
            composable(
                Routes.TV_SHOW,
                arguments = listOf(navArgument("tvId") { type = NavType.IntType }),
            ) { entry ->
                val tvId = entry.arguments?.getInt("tvId") ?: return@composable
                val container = TvApp.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvShowViewModel = viewModel(
                    key = "tvshow-$tvId",
                    factory = vmFactory {
                        TvShowViewModel(tvId, container.api)
                    },
                )
                TvShowScreen(
                    viewModel = vm,
                    onVideoClick = { videoId, nextId ->
                        navController.navigate(Routes.video(videoId, next = nextId))
                    },
                )
            }
            composable(
                Routes.VIDEO,
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
                val context = androidx.compose.ui.platform.LocalContext.current
                val container = TvApp.container(context)
                val vm: TvVideoViewModel = viewModel(
                    key = Routes.videoKey(videoId, fromStart, next),
                    factory = vmFactory {
                        TvVideoViewModel(
                            videoId,
                            container.api,
                            container.repository,
                            startFromBeginning = fromStart,
                            nextVideoId = next,
                        )
                    },
                )
                // One playback session per screen visit (see docs/adr/0001).
                // TV is streaming-only: no offline provider.
                val session = remember {
                    PlaybackSession.create(
                        context = context,
                        streamResolver = StreamResolver { id ->
                            container.api.getStreamInfo(id).hlsUrl
                        },
                    )
                }
                androidx.compose.runtime.DisposableEffect(session) {
                    onDispose { session.close() }
                }
                TvVideoScreen(
                    vm,
                    session = session,
                    onBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                )
            }
            composable(
                Routes.CHANNEL,
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
                val vm: ChannelViewModel = viewModel(
                    key = "channel-$authorId",
                    factory = vmFactory {
                        ChannelViewModel(
                            authorId,
                            name,
                            container.api,
                            container.repository,
                            pagination = false,
                        )
                    },
                )
                TvChannelScreen(
                    viewModel = vm,
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                )
            }
        }
    }
}

