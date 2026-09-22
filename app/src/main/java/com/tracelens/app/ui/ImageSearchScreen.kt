package com.tracelens.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracelens.app.export.ResultExport
import com.tracelens.app.search.ImageMode
import com.tracelens.app.ui.vm.SearchViewModel
import com.tracelens.app.web.SourceProviders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSearchScreen(vm: SearchViewModel, onBack: () -> Unit, onCollections: () -> Unit, onCompare: () -> Unit) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(vm.scopeCollectionId, collections) { vm.refreshImageStats() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::pickImageQuery) }
    val queryName = vm.imageQueryUri?.lastPathSegment ?: "query"

    TLScaffold(
        title = "Image Search", onBack = onBack,
        actions = {
            if (vm.imageHits.isNotEmpty()) ExportMenu(
                csv = { ResultExport.imageCsv(queryName, vm.imageMode.name, vm.imageHits) },
                json = { ResultExport.imageJson(queryName, vm.imageMode.name, vm.imageHits) },
                baseName = "tracelens_image_results",
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Box(Modifier.height(2.dp)) }
            if (vm.indexedImages == 0) item {
                Column {
                    InfoCard("Belum ada gambar ter-index${if (vm.scopeCollectionId != null) " di koleksi ini" else ""}. Tambahkan folder/gambar dulu di Collections.")
                    OutlinedButton(onClick = onCollections, modifier = Modifier.padding(top = 6.dp)) { Text("Buka Collections") }
                }
            }
            item {
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.imageQueryUri == null) "Pilih foto" else "Ganti foto") }
            }
            vm.error?.let { e -> item { InfoCard(e, isError = true) } }
            vm.imageQueryUri?.let { uri -> item { PhotoThumb(uri, 220.dp, Modifier.fillMaxWidth()) } }
            item { SectionTitle("Mode") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ImageMode.entries) { _, m ->
                        FilterChip(selected = vm.imageMode == m, onClick = { vm.updateImageMode(m) }, label = { Text(m.title) })
                    }
                }
            }
            item { SectionTitle("Cari di") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = vm.scopeCollectionId == null, onClick = { vm.scopeCollectionId = null }, label = { Text("Semua koleksi") }) }
                    itemsIndexed(collections) { _, c ->
                        FilterChip(selected = vm.scopeCollectionId == c.id, onClick = { vm.scopeCollectionId = c.id }, label = { Text(c.name) })
                    }
                }
            }
            item {
                Column {
                    Text("Similarity minimum: ${(vm.imageThreshold * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Slider(value = vm.imageThreshold, onValueChange = { vm.imageThreshold = it }, valueRange = 0.5f..0.98f)
                }
            }
            item {
                Button(
                    onClick = vm::runImageSearch,
                    enabled = vm.imageQueryUri != null && !vm.imageSearching && vm.indexedImages > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cari") }
            }
            if (vm.imageSearching) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Memproses…")
                }
            }
            if (vm.imageSearched) {
                item { SectionTitle("Hasil: ${vm.imageHits.size} gambar") }
                if (vm.imageHits.isEmpty()) item { InfoCard("Tidak ada hasil di atas ambang. Turunkan slider similarity.") }
                itemsIndexed(vm.imageHits) { _, h ->
                    ResultRow(
                        left = null, right = h.uri,
                        title = h.name,
                        subtitle = h.collectionName + if (h.viaCrop) " • cocok sebagai crop" else "",
                        percent = h.percent,
                        label = if (h.score >= 0.95f) "Near-identical" else "Similar",
                        onClick = { vm.openCompare(h); onCompare() },
                    )
                }
            }
            item {
                Column {
                    SectionTitle("Sumber publik (opsional)")
                    Text(
                        "Membuka halaman pencarian resmi di browser. Aplikasi tidak mengunggah foto; kamu memilih fotonya sendiri di sana.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SourceProviders.all.forEach { p ->
                item {
                    OutlinedButton(
                        onClick = { runCatching { ctx.startActivity(p.buildIntent()) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${p.name} — ${p.description}") }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}
