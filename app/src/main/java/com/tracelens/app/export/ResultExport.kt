package com.tracelens.app.export

import com.tracelens.app.search.FaceHit
import com.tracelens.app.search.ImageHit
import org.json.JSONArray
import org.json.JSONObject

/** Ekspor hasil (tanpa gambar): CSV atau JSON. */
object ResultExport {
    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun faceCsv(query: String, hits: List<FaceHit>): String = buildString {
        appendLine("query,rank,image_name,collection,image_uri,cosine,similarity_percent,label,face_box_norm")
        hits.forEachIndexed { i, h ->
            appendLine(
                listOf(
                    csv(query), (i + 1).toString(), csv(h.imageName), csv(h.collectionName), csv(h.imageUri),
                    "%.4f".format(h.cosine), h.percent.toString(), csv(h.label.text),
                    csv("%.3f,%.3f,%.3f,%.3f".format(h.nx1, h.ny1, h.nx2, h.ny2)),
                ).joinToString(",")
            )
        }
    }

    fun imageCsv(query: String, mode: String, hits: List<ImageHit>): String = buildString {
        appendLine("query,mode,rank,image_name,collection,image_uri,score_percent,full,region,color,via_crop")
        hits.forEachIndexed { i, h ->
            appendLine(
                listOf(
                    csv(query), csv(mode), (i + 1).toString(), csv(h.name), csv(h.collectionName), csv(h.uri),
                    h.percent.toString(), "%.3f".format(h.full), "%.3f".format(h.region), "%.3f".format(h.color), h.viaCrop.toString(),
                ).joinToString(",")
            )
        }
    }

    fun faceJson(query: String, hits: List<FaceHit>): String {
        val arr = JSONArray()
        hits.forEachIndexed { i, h ->
            arr.put(
                JSONObject().put("rank", i + 1).put("image", h.imageName).put("collection", h.collectionName)
                    .put("uri", h.imageUri).put("cosine", h.cosine.toDouble()).put("percent", h.percent).put("label", h.label.text)
                    .put("faceBox", JSONArray(listOf(h.nx1, h.ny1, h.nx2, h.ny2).map { it.toDouble() }))
            )
        }
        return JSONObject().put("type", "face_search").put("query", query)
            .put("note", "Visual similarity only; not an identification.").put("results", arr).toString(2)
    }

    fun imageJson(query: String, mode: String, hits: List<ImageHit>): String {
        val arr = JSONArray()
        hits.forEachIndexed { i, h ->
            arr.put(
                JSONObject().put("rank", i + 1).put("image", h.name).put("collection", h.collectionName).put("uri", h.uri)
                    .put("percent", h.percent).put("full", h.full.toDouble()).put("region", h.region.toDouble())
                    .put("color", h.color.toDouble()).put("viaCrop", h.viaCrop)
            )
        }
        return JSONObject().put("type", "image_search").put("query", query).put("mode", mode).put("results", arr).toString(2)
    }

}
