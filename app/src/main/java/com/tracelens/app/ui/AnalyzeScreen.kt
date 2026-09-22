package com.tracelens.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tracelens.app.ui.vm.AnalyzeViewModel

@Composable
fun AnalyzeScreen(vm: AnalyzeViewModel, onBack: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::analyze) }
    TLScaffold(title = "Analyze Image", onBack = onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(if (vm.uri == null) "Pilih foto" else "Ganti foto") }
            }
            vm.error?.let { e -> item { InfoCard(e, isError = true) } }
            vm.uri?.let { u -> item { PhotoThumb(u, 220.dp, Modifier.fillMaxWidth()) } }
            if (vm.busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Menganalisis…")
                }
            }
            vm.meta?.let { m ->
                m.sections.forEach { (title, rows) ->
                    item { SectionTitle(title) }
                    rows.forEach { (k, v) ->
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                                Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                            }
                        }
                    }
                }
            }
            if (vm.hashes.isNotEmpty()) {
                item { SectionTitle("Image hash") }
                vm.hashes.forEach { (k, v) ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                            Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                        }
                    }
                }
            }
            vm.faceInfo?.let { info ->
                item { SectionTitle("Wajah") }
                item { Text(info, style = MaterialTheme.typography.bodyMedium) }
                if (vm.faceThumbs.isNotEmpty()) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(vm.faceThumbs) { _, b ->
                            Image(
                                bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }
            if (vm.uri != null && !vm.busy) {
                item { SectionTitle("OCR (offline, Tesseract)") }
                item {
                    OutlinedButton(onClick = vm::runOcr, enabled = !vm.ocrRunning, modifier = Modifier.fillMaxWidth()) {
                        Text(if (vm.ocrRunning) "Membaca teks…" else "Jalankan OCR")
                    }
                }
                vm.ocr?.let { r ->
                    item {
                        Column {
                            Text("Confidence rata-rata: ${r.confidence}%", style = MaterialTheme.typography.bodySmall)
                            SelectionContainer {
                                Text(if (r.text.isBlank()) "(tidak ada teks terdeteksi)" else r.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }
}
