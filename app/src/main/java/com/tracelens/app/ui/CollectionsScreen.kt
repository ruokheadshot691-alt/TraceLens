package com.tracelens.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracelens.app.data.CollectionSummary
import com.tracelens.app.ui.vm.CollectionsViewModel

@Composable
fun CollectionsScreen(vm: CollectionsViewModel, onBack: () -> Unit) {
    val list by vm.summaries.collectAsStateWithLifecycle()
    val active by vm.activeCollection.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<CollectionSummary?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::addFolder) }
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addImages(it) }

    TLScaffold(
        title = "Collections", onBack = onBack,
        actions = {
            if (active != null) TextButton(onClick = vm::stop) { Text("Stop") }
            IconButton(onClick = vm::indexAll) { Icon(Icons.Filled.Refresh, contentDescription = "Index semua") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) { Text("Tambah folder") }
                        OutlinedButton(onClick = { imagesPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Tambah gambar") }
                    }
                    Text(
                        "Kamu yang menentukan folder/gambar yang di-index. Model aktif: ${vm.profile.title}. " +
                            "Embedding & hash hanya disimpan di HP ini.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (list.isEmpty()) item { InfoCard("Belum ada koleksi. Pilih folder foto atau beberapa gambar untuk mulai di-index.") }
            items(list, key = { it.id }) { c ->
                val busy = active == c.id
                val pending = c.total - c.done - c.failed
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${c.done}/${c.total} gambar • ${c.faces} wajah" + (if (c.failed > 0) " • ${c.failed} gagal" else "") +
                                (if (c.treeUri == null) " • gambar pilihan" else " • folder"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (busy) {
                            if (c.total > 0) LinearProgressIndicator(progress = { c.done.toFloat() / c.total }, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Memproses… ($pending menunggu)", style = MaterialTheme.typography.bodySmall)
                        } else if (pending > 0) {
                            Text("$pending gambar menunggu di-index", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.index(c.id) }, enabled = !busy) {
                                Text(if (c.treeUri != null) "Sinkron & index" else if (pending > 0) "Lanjutkan" else "Index ulang")
                            }
                            TextButton(onClick = { toDelete = c }) { Text("Hapus") }
                        }
                    }
                }
            }
            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus koleksi?") },
            text = { Text("\"${c.name}\" dan seluruh hasil index-nya (hash, embedding, thumbnail wajah) dihapus dari HP. File foto aslimu tidak disentuh.") },
            confirmButton = { TextButton(onClick = { vm.delete(c.id); toDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } },
        )
    }
}
