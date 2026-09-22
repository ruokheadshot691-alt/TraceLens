package com.tracelens.app.ui.vm

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracelens.app.TraceLensApp
import com.tracelens.app.core.ImageSignature
import com.tracelens.app.data.CollectionSummary
import com.tracelens.app.ml.FaceProfile
import com.tracelens.app.search.FaceHit
import com.tracelens.app.search.ImageHit
import com.tracelens.app.search.ImageMode
import com.tracelens.app.search.QueryFaces
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State bersama untuk Face Search, Image Search, dan layar Compare (di-scope ke Activity). */
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TraceLensApp).container

    val collections: StateFlow<List<CollectionSummary>> =
        c.collections.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profile: FaceProfile get() = c.engine.activeProfile()
    var scopeCollectionId by mutableStateOf<Long?>(null)
    var error by mutableStateOf<String?>(null)

    // ---------------- Face Search ----------------
    var faceQueryUri by mutableStateOf<Uri?>(null); private set
    var faceQuery by mutableStateOf<QueryFaces?>(null); private set
    var faceDetecting by mutableStateOf(false); private set
    var selectedFace by mutableIntStateOf(0)
    var faceThreshold by mutableFloatStateOf(c.faceSearcher.defaultThreshold())
    var faceSearching by mutableStateOf(false); private set
    var faceSearched by mutableStateOf(false); private set
    var faceHits by mutableStateOf<List<FaceHit>>(emptyList()); private set
    var indexedFaces by mutableIntStateOf(0); private set
    private var faceJob: Job? = null

    fun refreshFaceStats() = viewModelScope.launch {
        indexedFaces = c.faceSearcher.indexedFaceCount(scopeCollectionId)
        // ambang default bergantung profil aktif; sinkronkan bila user belum mengubahnya manual
        if (c.prefs.minFaceSim < 0f) faceThreshold = c.faceSearcher.defaultThreshold()
    }

    fun pickFaceQuery(uri: Uri) {
        faceJob?.cancel()
        faceQuery?.recycle()
        faceQuery = null; faceHits = emptyList(); faceSearched = false; selectedFace = 0; error = null
        faceQueryUri = uri
        faceDetecting = true
        faceJob = viewModelScope.launch {
            try {
                faceQuery = c.faceSearcher.detect(uri)
            } catch (e: Exception) {
                error = e.message ?: "Gagal mendeteksi wajah"
            } finally {
                faceDetecting = false
            }
        }
    }

    fun runFaceSearch() {
        val q = faceQuery?.faces?.getOrNull(selectedFace) ?: return
        faceJob?.cancel()
        faceSearching = true; error = null
        faceJob = viewModelScope.launch {
            try {
                faceHits = c.faceSearcher.search(q.embedding, scopeCollectionId, faceThreshold)
                faceSearched = true
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) error = e.message ?: "Pencarian gagal"
            } finally {
                faceSearching = false
            }
        }
    }

    // ---------------- Image Search ----------------
    var imageQueryUri by mutableStateOf<Uri?>(null); private set
    private var imageSig: ImageSignature? = null
    var imageMode by mutableStateOf(ImageMode.NEAR_DUPLICATE); private set
    var imageThreshold by mutableFloatStateOf(c.imageSearcher.defaultThreshold(ImageMode.NEAR_DUPLICATE))
    var imageSearching by mutableStateOf(false); private set
    var imageSearched by mutableStateOf(false); private set
    var imageHits by mutableStateOf<List<ImageHit>>(emptyList()); private set
    var indexedImages by mutableIntStateOf(0); private set
    private var imageJob: Job? = null

    fun refreshImageStats() = viewModelScope.launch { indexedImages = c.imageSearcher.indexedCount(scopeCollectionId) }

    fun setImageMode(m: ImageMode) {
        imageMode = m
        imageThreshold = c.imageSearcher.defaultThreshold(m)
    }

    fun pickImageQuery(uri: Uri) {
        imageJob?.cancel()
        imageQueryUri = uri; imageHits = emptyList(); imageSearched = false; error = null; imageSig = null
        imageSearching = true
        imageJob = viewModelScope.launch {
            try {
                imageSig = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.imageSearcher.signatureOf(uri) }
                runImageSearchInternal()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) error = e.message ?: "Gagal membaca gambar"
            } finally {
                imageSearching = false
            }
        }
    }

    fun runImageSearch() {
        imageJob?.cancel()
        imageSearching = true; error = null
        imageJob = viewModelScope.launch {
            try {
                runImageSearchInternal()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) error = e.message ?: "Pencarian gagal"
            } finally {
                imageSearching = false
            }
        }
    }

    private suspend fun runImageSearchInternal() {
        val sig = imageSig ?: return
        val self = imageQueryUri?.toString()
        imageHits = c.imageSearcher.search(sig, imageMode, scopeCollectionId, imageThreshold).filter { it.uri != self }
        imageSearched = true
    }

    // ---------------- Compare ----------------
    var compareFace by mutableStateOf<FaceHit?>(null); private set
    var compareImage by mutableStateOf<ImageHit?>(null); private set

    fun openCompare(h: FaceHit) { compareFace = h; compareImage = null }
    fun openCompare(h: ImageHit) { compareImage = h; compareFace = null }

    fun faceThumbFile(faceId: Long) = c.thumbs.file(faceId)

    override fun onCleared() {
        faceQuery?.recycle()
        super.onCleared()
    }
}
