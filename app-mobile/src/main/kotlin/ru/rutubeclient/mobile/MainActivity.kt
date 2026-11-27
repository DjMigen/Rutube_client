package ru.rutubeclient.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.rutubeclient.mobile.di.vmFactory
import ru.rutubeclient.mobile.player.CastManager
import ru.rutubeclient.mobile.ui.screens.catalog.CatalogScreen
import ru.rutubeclient.mobile.ui.screens.catalog.CatalogViewModel
import ru.rutubeclient.mobile.ui.screens.catalog.TvShowScreen
import ru.rutubeclient.mobile.ui.screens.catalog.TvShowViewModel
import ru.rutubeclient.mobile.ui.screens.channel.ChannelScreen
import ru.rutubeclient.mobile.ui.screens.channel.ChannelViewModel
import ru.rutubeclient.mobile.ui.screens.home.HomeScreen
import ru.rutubeclient.mobile.ui.screens.home.HomeViewModel
import ru.rutubeclient.mobile.ui.screens.library.LibraryScreen
import ru.rutubeclient.mobile.ui.screens.library.LibraryViewModel
import ru.rutubeclient.mobile.ui.screens.search.SearchScreen
import ru.rutubeclient.mobile.ui.screens.search.SearchViewModel
import ru.rutubeclient.mobile.ui.screens.subscriptions.SubscriptionsScreen
import ru.rutubeclient.mobile.ui.screens.subscriptions.SubscriptionsViewModel
import ru.rutubeclient.mobile.ui.screens.video.VideoScreen
import ru.rutubeclient.mobile.ui.screens.video.VideoViewModel
import ru.rutubeclient.mobile.ui.theme.RuTubeTheme

object Routes {
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

    private lateinit var castManager: CastManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        castManager = CastManager(this)
        castManager.initialize()
        setContent {
            RuTubeTheme {
                AppNavHost(castManager)
            }
        }
    }
}

private data class TopLevel(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, "Главная", Icons.Default.Home),
    TopLevel(Routes.SEARCH, "Поиск", Icons.Default.Search),
    TopLevel(Routes.SUBSCRIPTIONS, "Подписки", Icons.Default.Subscriptions),
    TopLevel(Routes.LIBRARY, "Библиотека", Icons.Default.VideoLibrary),
    TopLevel(Routes.CATALOG, "Каталог", Icons.Default.LocalMovies),
)

@Composable
fun AppNavHost(castManager: CastManager) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: HomeViewModel = viewModel(factory = vmFactory {
                    HomeViewModel(container.api, container.repository)
                })
                HomeScreen(
                    vm,
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                    onWatchFromStart = { navController.navigate(Routes.video(it, fromStart = true)) },
                )
            }
            composable(Routes.SEARCH) {
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: SearchViewModel = viewModel(factory = vmFactory {
                    SearchViewModel(container.api, container.repository)
                })
                SearchScreen(vm, onVideoClick = { navController.navigate(Routes.video(it)) })
            }
            composable(Routes.SUBSCRIPTIONS) {
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: SubscriptionsViewModel = viewModel(factory = vmFactory {
                    SubscriptionsViewModel(container.repository)
                })
                SubscriptionsScreen(
                    vm,
                    onChannelClick = { id, name -> navController.navigate(Routes.channel(id, name)) },
                )
            }
            composable(Routes.LIBRARY) {
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: LibraryViewModel = viewModel(factory = vmFactory {
                    LibraryViewModel(container.repository, container.downloadsManager)
                })
                LibraryScreen(vm, onVideoClick = { navController.navigate(Routes.video(it)) })
            }
            composable(Routes.CATALOG) {
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: CatalogViewModel = viewModel(factory = vmFactory {
                    CatalogViewModel(container.api)
                })
                CatalogScreen(
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
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: TvShowViewModel = viewModel(
                    key = "tvshow-$tvId",
                    factory = vmFactory {
                        TvShowViewModel(tvId, container.api)
                    },
                )
                TvShowScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
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
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: VideoViewModel = viewModel(
                    key = "video-$videoId-$fromStart-$next",
                    factory = vmFactory {
                        VideoViewModel(
                            videoId,
                            container.api,
                            container.repository,
                            container.downloadsManager,
                            startFromBeginning = fromStart,
                            nextVideoId = next,
                        )
                    },
                )
                VideoScreen(
                    viewModel = vm,
                    castManager = castManager,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                    onChannelClick = { id, name -> navController.navigate(Routes.channel(id, name)) },
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
                val container = App.container(androidx.compose.ui.platform.LocalContext.current)
                val vm: ChannelViewModel = viewModel(
                    key = "channel-$authorId",
                    factory = vmFactory {
                        ChannelViewModel(authorId, name, container.api, container.repository)
                    },
                )
                ChannelScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(Routes.video(it)) },
                )
            }
        }
    }
}