package com.tracelens.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(c: CollectionEntity): Long

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun get(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections ORDER BY createdAt")
    suspend fun all(): List<CollectionEntity>

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT name FROM collections WHERE id = :id")
    suspend fun nameOf(id: Long): String?

    @Query(
        """SELECT c.id AS id, c.name AS name, c.treeUri AS treeUri,
            (SELECT COUNT(*) FROM images i WHERE i.collectionId = c.id) AS total,
            (SELECT COUNT(*) FROM images i WHERE i.collectionId = c.id AND i.status = 1) AS done,
            (SELECT COUNT(*) FROM images i WHERE i.collectionId = c.id AND i.status = 2) AS failed,
            (SELECT COALESCE(SUM(i.faceCount), 0) FROM images i WHERE i.collectionId = c.id) AS faces
           FROM collections c ORDER BY c.createdAt DESC"""
    )
    fun observeSummaries(): Flow<List<CollectionSummary>>
}

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<ImageEntity>): List<Long>

    @Update
    suspend fun update(img: ImageEntity)

    @Query("SELECT id, uri, modified, size, status FROM images WHERE collectionId = :cid")
    suspend fun stubs(cid: Long): List<ImageStub>

    /** Menunggu: belum diproses, atau sudah diproses tapi dengan profil wajah yang berbeda dari [modelId]. */
    @Query(
        """SELECT * FROM images WHERE collectionId = :cid
           AND (status = 0 OR (status = 1 AND faceModelId IS NOT :modelId)) ORDER BY id LIMIT :limit"""
    )
    suspend fun pending(cid: Long, modelId: String, limit: Int): List<ImageEntity>

    @Query(
        """SELECT COUNT(*) FROM images WHERE collectionId = :cid
           AND (status = 0 OR (status = 1 AND faceModelId IS NOT :modelId))"""
    )
    suspend fun pendingCount(cid: Long, modelId: String): Int

    @Query("UPDATE images SET status = 0 WHERE collectionId = :cid AND status = 2")
    suspend fun retryErrors(cid: Long)

    @Query("UPDATE images SET status = 0, modified = :modified, size = :size, hashBlob = NULL, colorBlob = NULL, faceModelId = NULL, faceCount = 0 WHERE id = :id")
    suspend fun markChanged(id: Long, modified: Long, size: Long)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT id, collectionId, uri, name, width, height, hashBlob, colorBlob FROM images WHERE status = 1 AND hashBlob IS NOT NULL AND id > :after AND (:cid IS NULL OR collectionId = :cid) ORDER BY id LIMIT :limit")
    suspend fun hashPage(after: Long, cid: Long?, limit: Int): List<ImageHashRow>

    @Query("SELECT id, collectionId, uri, name, width, height FROM images WHERE id = :id")
    suspend fun info(id: Long): ImageInfo?

    @Query("SELECT COUNT(*) FROM images WHERE status = 1 AND hashBlob IS NOT NULL AND (:cid IS NULL OR collectionId = :cid)")
    suspend fun indexedCount(cid: Long?): Int
}

@Dao
interface FaceDao {
    @Insert
    suspend fun insertAll(list: List<FaceEntity>): List<Long>

    @Query("DELETE FROM faces WHERE imageId = :imageId")
    suspend fun deleteForImage(imageId: Long)

    @Query("SELECT id FROM faces WHERE imageId = :imageId")
    suspend fun idsForImage(imageId: Long): List<Long>

    @Query("SELECT f.id FROM faces f JOIN images i ON i.id = f.imageId WHERE i.collectionId = :cid")
    suspend fun idsForCollection(cid: Long): List<Long>

    @Query("SELECT f.id AS faceId, f.imageId AS imageId, f.embedding AS embedding, f.x1 AS x1, f.y1 AS y1, f.x2 AS x2, f.y2 AS y2, f.score AS detScore FROM faces f JOIN images i ON i.id = f.imageId WHERE f.modelId = :modelId AND f.id > :after AND (:cid IS NULL OR i.collectionId = :cid) ORDER BY f.id LIMIT :limit")
    suspend fun page(modelId: String, after: Long, cid: Long?, limit: Int): List<FaceRow>

    @Query("SELECT COUNT(*) FROM faces f JOIN images i ON i.id = f.imageId WHERE f.modelId = :modelId AND (:cid IS NULL OR i.collectionId = :cid)")
    suspend fun count(modelId: String, cid: Long?): Int
}
