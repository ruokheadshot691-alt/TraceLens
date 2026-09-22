package com.tracelens.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracelens.app.ui.vm.SearchViewModel

@Composable
fun HomeScreen(
    vm: SearchViewModel,
    onFace: () -> Unit, onImage: () -> Unit, onAnalyze: () -> Unit, onCollections: () -> Unit, onSettings: () -> Unit,
) {
    val cols by vm.collections.collectAsStateWithLifecycle()
    val images = cols.sumOf { it.done }
    val faces = cols.sumOf { it.faces }
    TLScaffold(
        title = "TraceLens", onBack = null,
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Pengaturan") } },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cari kemiripan wajah & gambar dari foto. Semua diproses di HP ini.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            BigButton("FACE SEARCH", "Cari wajah yang mirip di koleksi lokal", onFace)
            BigButton("IMAGE SEARCH", "Gambar identik, resize, crop, kompresi, atau mirip", onImage)
            BigButton("ANALYZE IMAGE", "Metadata/EXIF, hash, wajah, dan OCR", onAnalyze)
            Spacer(Modifier.height(4.dp))
            BigButton("COLLECTIONS", "$images gambar ter-index • $faces wajah", onCollections, primary = false)
            Text(
                "Hasil wajah = \"Visual Similarity\" / \"Possible Match\" — bukan identifikasi nama seseorang. " +
                    "Foto tidak diunggah; database tersimpan lokal.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
