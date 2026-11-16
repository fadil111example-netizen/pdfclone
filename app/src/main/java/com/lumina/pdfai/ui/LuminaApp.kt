package com.lumina.pdfai.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumina.pdfai.ui.home.HomeScreen
import com.lumina.pdfai.ui.reader.AiReaderScreen
import com.lumina.pdfai.ui.reader.ReaderRoute
import com.lumina.pdfai.ui.settings.AboutScreen
import com.lumina.pdfai.ui.settings.SettingsScreen
import com.lumina.pdfai.ui.viewmodel.LibraryViewModel
import android.util.Base64

/**
 * Route table.
 *
 * Document uris are base64-url encoded before they enter a route: content uris
 * contain slashes and percent escapes that the navigation library would
 * otherwise split apart.
 */
object Routes {
    const val HOME = "home"
    const val READER = "reader/{key}"
    const val AI_READER = "ai/{key}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun reader(uri: String): String = "reader/${encode(uri)}"
    fun aiReader(uri: String): String = "ai/${encode(uri)}"

    fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    fun decode(key: String): String = runCatching {
        String(Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrDefault("")
}

@Composable
fun LuminaApp(
    incomingDocument: String? = null,
    onIncomingHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(incomingDocument) {
        val raw = incomingDocument ?: return@LaunchedEffect
        if (raw.isBlank()) return@LaunchedEffect
        navController.navigate(Routes.reader(raw))
        onIncomingHandled()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(220))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(220))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180))
        }
    ) {
        composable(Routes.HOME) {
            val viewModel: LibraryViewModel = viewModel()
            HomeScreen(
                viewModel = viewModel,
                onOpenDocument = { uri -> navController.navigate(Routes.reader(uri)) },
                onListenDocument = { uri -> navController.navigate(Routes.aiReader(uri)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString("key").orEmpty()
            ReaderRoute(
                documentUri = Routes.decode(key),
                onBack = { navController.popBackStack() },
                onListen = { target -> navController.navigate(Routes.aiReader(target)) }
            )
        }

        composable(
            route = Routes.AI_READER,
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString("key").orEmpty()
            AiReaderScreen(
                documentUri = Routes.decode(key),
                onBack = { navController.popBackStack() },
                onOpenPdf = { target, page ->
                    navController.navigate(Routes.reader(target))
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}