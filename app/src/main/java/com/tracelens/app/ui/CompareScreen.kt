package com.tracelens.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tracelens.app.ui.vm.SearchViewModel

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodyMedium)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CompareScreen(vm: SearchViewModel, onBack: () -> Unit) {
    val face = vm.compareFace
    val img = vm.compareImage
    TLScaffold(title = "Compare", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (face != null) {
                val qFace = vm.faceQuery?.faces?.getOrNull(vm.selectedFace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Panel("Query", Modifier.weight(1f)) {
                        if (qFace != null) Image(
                            bitmap = qFace.thumb.asImageBitmap(), contentDescription = "Wajah query", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        PhotoThumb(vm.faceQueryUri, 140.dp)
                    }
                    Panel("Hasil", Modifier.weight(1f)) {
                        PhotoThumb(vm.faceThumbFile(face.faceId), 140.dp)
                        PhotoThumb(face.imageUri, 140.dp)
                    }
                }
                Text("${face.percent}%", style = MaterialTheme.typography.displayMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(face.label.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                KV("Cosine similarity", "%.3f".format(face.cosine))
                KV("Ambang \"Possible match\"", "%.2f".format(vm.profile.possible))
                KV("Ambang \"Strong\"", "%.2f".format(vm.profile.strong))
                KV("File", face.imageName)
                KV("Koleksi", face.collectionName)
                KV("Model", vm.profile.title)
                Text(
                    "Ini kemiripan visual antar embedding wajah — bukan identifikasi atau bukti bahwa dua foto adalah orang yang sama. " +
                        "Pose, cahaya, usia, dan kualitas foto sangat mempengaruhi skor.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (img != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Panel("Query", Modifier.weight(1f)) { PhotoThumb(vm.imageQueryUri, 160.dp) }
                    Panel("Hasil", Modifier.weight(1f)) { PhotoThumb(img.uri, 160.dp) }
                }
                Text("${img.percent}%", style = MaterialTheme.typography.displayMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                KV("Mode", vm.imageMode.title)
                KV("Gambar penuh vs penuh", "${(img.full * 100).toInt()}%")
                KV("Kecocokan jendela crop terbaik", "${(img.region * 100).toInt()}%")
                KV("Tata letak warna", "${(img.color * 100).toInt()}%")
                KV("Terdeteksi sebagai crop", if (img.viaCrop) "Ya" else "Tidak")
                KV("File", img.name)
                KV("Koleksi", img.collectionName)
                Text(
                    "Skor dari jarak Hamming pHash+dHash (128 bit). ≥95% ≈ gambar sama; 80–95% ≈ versi berbeda (resize/crop/kompresi) atau sangat mirip.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("Pilih satu hasil dari Face Search atau Image Search untuk dibandingkan.")
            }
        }
    }
}
