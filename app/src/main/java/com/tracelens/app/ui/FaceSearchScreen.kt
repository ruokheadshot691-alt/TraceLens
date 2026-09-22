package com.tracelens.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracelens.app.export.ResultExport
import com.tracelens.app.ui.vm.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceSearchScreen(vm: SearchViewModel, onBack: () -> Unit, onCollections: () -> Unit, onCompare: () -> Unit) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    LaunchedEffect(vm.scopeCollectionId, collections) { vm.refreshFaceStats() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::pickFaceQuery) }
    val queryName = vm.faceQueryUri?.lastPathSegment ?: "query"

    TLScaffold(
        title = "Face Search", onBack = onBack,
        actions = {
            if (vm.faceHits.isNotEmpty()) ExportMenu(
                csv = { ResultExport.faceCsv(queryName, vm.faceHits) },
                json = { ResultExport.faceJson(queryName, vm.faceHits) },
                baseName = "tracelens_face_results",
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Box(Modifier.height(2.dp)) }
            if (vm.indexedFaces == 0) item {
                Column {
                    InfoCard("Belum ada wajah ter-index${if (vm.scopeCollectionId != null) " di koleksi ini" else ""}. Tambahkan folder/gambar dulu di Collections.")
                    OutlinedButton(onClick = onCollections, modifier = Modifier.padding(top = 6.dp)) { Text("Buka Collections") }
                }
            }
            item {
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.faceQueryUri == null) "Pilih foto" else "Ganti foto") }
            }
            vm.error?.let { e -> item { InfoCard(e, isError = true) } }
            vm.faceQueryUri?.let { uri ->
                item { PhotoThumb(uri, 220.dp, Modifier.fillMaxWidth()) }
            }
            if (vm.faceDetecting) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Mendeteksi wajah…")
                }
            }
            vm.faceQuery?.let { q ->
                if (q.faces.isEmpty()) item { InfoCard("Tidak ada wajah terdeteksi pada foto ini.") }
                else {
                    item { SectionTitle("Wajah terdeteksi (${q.faces.size}) — pilih satu") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(q.faces) { i, f ->
                                val sel = i == vm.selectedFace
                                Image(
                                    bitmap = f.thumb.asImageBitmap(), contentDescription = "Wajah ${i + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                                        .clickable { vm.selectedFace = i }
                                        .then(
                                            if (sel) Modifier.androidBorder() else Modifier
                                        ),
                                )
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
                            Text("Similarity minimum: ${(vm.faceThreshold * 100).toInt()}%  (default profil: ${(vm.profile.possible * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                            Slider(value = vm.faceThreshold, onValueChange = { vm.faceThreshold = it }, valueRange = 0.2f..0.9f)
                        }
                    }
                    item {
                        Button(onClick = vm::runFaceSearch, enabled = !vm.faceSearching && vm.indexedFaces > 0, modifier = Modifier.fillMaxWidth()) {
                            Text(if (vm.faceSearching) "Mencari…" else "Cari wajah mirip")
                        }
                    }
                }
            }
            if (vm.faceSearched) {
                item { SectionTitle("Hasil: ${vm.faceHits.size} kemiripan visual") }
                if (vm.faceHits.isEmpty()) item { InfoCard("Tidak ada hasil di atas ambang. Turunkan slider similarity untuk melihat kandidat yang lebih lemah.") }
                itemsIndexed(vm.faceHits) { _, h ->
                    ResultRow(
                        left = vm.faceThumbFile(h.faceId), right = h.imageUri,
                        title = h.imageName, subtitle = h.collectionName,
                        percent = h.percent, label = h.label.text,
                        onClick = { vm.openCompare(h); onCompare() },
                    )
                }
                item { Text("Skor = cosine similarity antar embedding wajah. Ini kemiripan visual, bukan bukti identitas.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

private fun Modifier.androidBorder(): Modifier = this.then(
    Modifier.border(BorderStroke(3.dp, androidx.compose.ui.graphics.Color(0xFF6650A4)), RoundedCornerShape(10.dp))
)
