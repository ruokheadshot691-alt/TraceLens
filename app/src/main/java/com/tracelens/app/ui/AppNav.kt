package com.tracelens.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tracelens.app.ui.vm.AnalyzeViewModel
import com.tracelens.app.ui.vm.CollectionsViewModel
import com.tracelens.app.ui.vm.SearchViewModel
import com.tracelens.app.ui.vm.SettingsViewModel

@Composable
fun AppNav(
    search: SearchViewModel,
    collections: CollectionsViewModel,
    analyze: AnalyzeViewModel,
    settings: SettingsViewModel,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = search,
                onFace = { nav.navigate("face") },
                onImage = { nav.navigate("image") },
                onAnalyze = { nav.navigate("analyze") },
                onCollections = { nav.navigate("collections") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("face") { FaceSearchScreen(search, { nav.popBackStack() }, { nav.navigate("collections") }, { nav.navigate("compare") }) }
        composable("image") { ImageSearchScreen(search, { nav.popBackStack() }, { nav.navigate("collections") }, { nav.navigate("compare") }) }
        composable("analyze") { AnalyzeScreen(analyze) { nav.popBackStack() } }
        composable("collections") { CollectionsScreen(collections) { nav.popBackStack() } }
        composable("compare") { CompareScreen(search) { nav.popBackStack() } }
        composable("settings") { SettingsScreen(settings) { nav.popBackStack() } }
    }
}
