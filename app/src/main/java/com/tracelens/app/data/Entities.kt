package com.tracelens.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Koleksi = satu folder (treeUri) atau kumpulan gambar pilihan (treeUri == null). */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val treeUri: String?,
    val createdAt: Long,
)

object ImageStatus {
    const val PENDING = 0
    const val DONE = 1
    const val ERROR = 2
}

@Entity(
    tableName = "images",
    foreignKeys = [ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId"), Index(value = ["collectionId", "uri"], unique = true)],
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val uri: String,
    val name: String,
    val mime: String?,
    val size: Long,
    val modified: Long,
    val width: Int = 0,
    val height: Int = 0,
    val exifDate: String? = null,
    /** 37 jendela × (pHash,dHash) × 8 byte, little-endian. */
    val hashBlob: ByteArray? = null,
    /** 4×4×RGB. */
    val colorBlob: ByteArray? = null,
    /** id profil model yang dipakai menghitung wajah; null = belum. */
    val faceModelId: String? = null,
    val faceCount: Int = 0,
    val status: Int = ImageStatus.PENDING,
    val indexedAt: Long = 0,
)

@Entity(
    tableName = "faces",
    foreignKeys = [ForeignKey(entity = ImageEntity::class, parentColumns = ["id"], childColumns = ["imageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("imageId"), Index("modelId")],
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: Long,
    val modelId: String,
    /** vektor ter-L2-normalisasi, float32 little-endian. */
    val embedding: ByteArray,
    /** box ternormalisasi 0..1 terhadap gambar yang sudah dirotasi sesuai EXIF. */
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val score: Float,
)

// ---- Proyeksi hasil query ----
data class CollectionSummary(
    val id: Long, val name: String, val treeUri: String?,
    val total: Int, val done: Int, val failed: Int, val faces: Int,
)

data class ImageStub(val id: Long, val uri: String, val modified: Long, val size: Long, val status: Int)

data class ImageHashRow(
    val id: Long, val collectionId: Long, val uri: String, val name: String,
    val width: Int, val height: Int, val hashBlob: ByteArray, val colorBlob: ByteArray,
)

data class FaceRow(
    val faceId: Long, val imageId: Long, val embedding: ByteArray,
    val x1: Float, val y1: Float, val x2: Float, val y2: Float, val detScore: Float,
)

data class ImageInfo(val id: Long, val collectionId: Long, val uri: String, val name: String, val width: Int, val height: Int)
