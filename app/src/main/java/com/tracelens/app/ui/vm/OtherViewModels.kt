package com.tracelens.app.ui.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracelens.app.TraceLensApp
import com.tracelens.app.core.ImageHash
import com.tracelens.app.data.CollectionSummary
import com.tracelens.app.index.IndexWorker
import com.tracelens.app.meta.ImageMeta
import com.tracelens.app.meta.MetadataReader
import com.tracelens.app.ml.FaceProfile
import com.tracelens.app.ml.ImageIo
import com.tracelens.app.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class CollectionsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = (app as TraceLensApp).container

    val summaries: StateFlow<List<CollectionSummary>> =
        c.collections.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeCollection: StateFlow<Long?> = c.indexer.activeCollection
    val profile: FaceProfile get() = c.engine.activeProfile()

    fun addFolder(tree: Uri) = viewModelScope.launch {
        val id = c.collections.createFolder(tree)
        IndexWorker.enqueue(app, id)
    }

    fun addImages(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        val name = "Gambar pilihan " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date())
        val id = c.collections.createFromImages(name, uris)
        IndexWorker.enqueue(app, id)
    }

    fun index(id: Long) = IndexWorker.enqueue(app, id)
    fun indexAll() = IndexWorker.enqueue(app, null)
    fun stop() = IndexWorker.cancel(app)
    fun delete(id: Long) = viewModelScope.launch { c.collections.delete(id) }
}

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = (app as TraceLensApp).container
    val prefs = c.prefs
    var profileId by mutableStateOf(prefs.profileId)
    var lowRam by mutableStateOf(prefs.lowRam); private set
    var minImageSim by mutableStateOf(prefs.minImageSim); private set
    var cleared by mutableStateOf(false)

    fun availableProfiles(): List<Pair<FaceProfile, Boolean>> = FaceProfile.entries.map { it to it.isAvailable(app) }

    fun selectProfile(p: FaceProfile) {
        prefs.profileId = p.id; profileId = p.id
        prefs.minFaceSim = -1f
        c.engine.release()
    }

    fun setLowRam(v: Boolean) { prefs.lowRam = v; lowRam = v; c.engine.release() }
    fun setMinImageSim(v: Float) { prefs.minImageSim = v; minImageSim = v }

    fun clearAll() = viewModelScope.launch {
        IndexWorker.cancel(app)
        withContext(Dispatchers.IO) {
            c.db.clearAllTables()
            c.thumbs.deleteAll()
        }
        cleared = true
    }
}

class AnalyzeViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = (app as TraceLensApp).container

    var uri by mutableStateOf<Uri?>(null); private set
    var busy by mutableStateOf(false); private set
    var meta by mutableStateOf<ImageMeta?>(null); private set
    var hashes by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set
    var faceThumbs by mutableStateOf<List<Bitmap>>(emptyList()); private set
    var faceInfo by mutableStateOf<String?>(null); private set
    var ocr by mutableStateOf<OcrResult?>(null); private set
    var ocrRunning by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun analyze(u: Uri) {
        uri = u; busy = true; error = null; ocr = null; meta = null; hashes = emptyList(); faceThumbs = emptyList(); faceInfo = null
        viewModelScope.launch {
            try {
                meta = withContext(Dispatchers.IO) { MetadataReader.read(app, u) }
                withContext(Dispatchers.Default) {
                    val dec = ImageIo.decode(app, u, c.prefs.decodeMaxSide) ?: error("Gambar tidak bisa dibaca")
                    try {
                        val sig = ImageHash.signature(ImageHash.buildThumb(ImageIo.pixels(dec.bitmap), dec.bitmap.width, dec.bitmap.height))
                        hashes = listOf(
                            "pHash" to ImageHash.toHex(sig.pHash(0)),
                            "dHash" to ImageHash.toHex(sig.dHash(0)),
                            "Jendela crop" to "${sig.windowCount} (untuk pencocokan crop)",
                        )
                        val faces = c.engine.analyze(dec.bitmap)
                        faceThumbs = faces.map { it.thumb }
                        faceInfo = "${faces.size} wajah terdeteksi (model: ${c.engine.activeProfile().title})"
                    } finally {
                        dec.bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Analisis gagal"
            } finally {
                busy = false
            }
        }
    }

    fun runOcr() {
        val u = uri ?: return
        ocrRunning = true; ocr = null
        viewModelScope.launch {
            try {
                ocr = withContext(Dispatchers.Default) {
                    val dec = ImageIo.decode(app, u, 2000) ?: error("Gambar tidak bisa dibaca")
                    try { c.ocr.recognize(dec.bitmap) } finally { dec.bitmap.recycle() }
                }
            } catch (e: Exception) {
                error = e.message ?: "OCR gagal"
            } finally {
                ocrRunning = false
            }
        }
    }
}
