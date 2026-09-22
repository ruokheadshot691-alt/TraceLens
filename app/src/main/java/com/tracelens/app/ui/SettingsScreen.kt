package com.tracelens.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tracelens.app.ui.vm.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TLScaffold(title = "Pengaturan", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Model wajah")
            vm.availableProfiles().forEach { (p, available) ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = available) { vm.selectProfile(p) },
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(selected = vm.profileId == p.id, onClick = { vm.selectProfile(p) }, enabled = available)
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(p.title + if (!available) "  (belum terpasang)" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(p.licenseNote, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            InfoCard("Mengganti model membuat embedding lama tidak kompatibel. Buka Collections → Sinkron & index untuk menghitung ulang wajah dengan model baru.")

            SectionTitle("Performa")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Mode RAM rendah", style = MaterialTheme.typography.bodyLarge)
                    Text("Decode gambar lebih kecil (800 px), thread lebih sedikit.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.lowRam, onCheckedChange = vm::setLowRam)
            }

            SectionTitle("Ambang default Image Search")
            Text("${(vm.minImageSim * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(value = vm.minImageSim, onValueChange = vm::setMinImageSim, valueRange = 0.5f..0.98f)

            SectionTitle("Privasi & data")
            Text(
                "Foto diproses lokal. Tidak ada login, tidak ada upload, tidak ada izin lokasi/internet wajib. " +
                    "Database (hash, embedding, thumbnail wajah) ada di penyimpanan privat aplikasi dan tidak ikut backup cloud.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Hapus semua data index") }
            if (vm.cleared) Text("Semua data index dihapus.", style = MaterialTheme.typography.bodySmall)

            SectionTitle("Tentang")
            Text(
                "Komponen open-source: ONNX Runtime (MIT), Tesseract4Android + tessdata_fast (Apache-2.0), Room/WorkManager/Coil (Apache-2.0), " +
                    "Ultra-Light-Fast-Generic-Face-Detector-1MB (MIT), MobileFaceNet (syaringan357 MIT / sirius-ai Apache-2.0). " +
                    "Detail lisensi model: docs/THIRD_PARTY.md.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Hapus semua data index?") },
        text = { Text("Semua koleksi, hash, embedding, dan thumbnail wajah dihapus. File fotomu tidak disentuh.") },
        confirmButton = { TextButton(onClick = { vm.clearAll(); confirm = false }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } },
    )
}
