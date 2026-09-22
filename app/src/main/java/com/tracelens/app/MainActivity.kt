package com.tracelens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tracelens.app.ui.AppNav
import com.tracelens.app.ui.theme.AppTheme
import com.tracelens.app.ui.vm.AnalyzeViewModel
import com.tracelens.app.ui.vm.CollectionsViewModel
import com.tracelens.app.ui.vm.SearchViewModel
import com.tracelens.app.ui.vm.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val searchVm: SearchViewModel by viewModels()
    private val collectionsVm: CollectionsViewModel by viewModels()
    private val analyzeVm: AnalyzeViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNav(searchVm, collectionsVm, analyzeVm, settingsVm)
            }
        }
    }
}
