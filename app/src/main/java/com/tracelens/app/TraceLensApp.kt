package com.tracelens.app

import android.app.Application
import com.tracelens.app.data.AppDatabase
import com.tracelens.app.data.Prefs
import com.tracelens.app.index.CollectionRepository
import com.tracelens.app.index.Indexer
import com.tracelens.app.ml.FaceEngine
import com.tracelens.app.ml.FaceThumbStore
import com.tracelens.app.ocr.OcrEngine
import com.tracelens.app.search.FaceSearcher
import com.tracelens.app.search.ImageSearcher

class AppContainer(val app: Application) {
    val prefs = Prefs(app)
    val db: AppDatabase by lazy { AppDatabase.create(app) }
    val thumbs = FaceThumbStore(app)
    val engine = FaceEngine(app, prefs)
    val indexer: Indexer by lazy { Indexer(app, db, engine, prefs, thumbs) }
    val collections: CollectionRepository by lazy { CollectionRepository(app, db, thumbs) }
    val faceSearcher: FaceSearcher by lazy { FaceSearcher(app, db, engine, prefs) }
    val imageSearcher: ImageSearcher by lazy { ImageSearcher(app, db, prefs) }
    val ocr = OcrEngine(app)
}

class TraceLensApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
