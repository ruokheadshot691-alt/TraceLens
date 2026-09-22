package com.tracelens.app.core

/** Decoder keluaran detektor. Pure Kotlin supaya bisa di-unit-test tanpa model. */
object RfbDecoder {
    /**
     * Ultra-Light-Fast-Generic-Face-Detector (RFB). Model sudah meng-decode box → tinggal threshold + NMS.
     * @param scores [N*2] (background, face) ; @param boxes [N*4] xyxy ternormalisasi 0..1
     */
    fun decode(
        scores: FloatArray, boxes: FloatArray, imgW: Int, imgH: Int,
        scoreThr: Float = 0.7f, iou: Float = 0.3f,
    ): List<FaceBox> {
        val n = scores.size / 2
        val cand = ArrayList<FaceBox>()
        for (i in 0 until n) {
            val s = scores[2 * i + 1]
            if (s < scoreThr) continue
            cand.add(
                FaceBox(
                    boxes[4 * i] * imgW, boxes[4 * i + 1] * imgH,
                    boxes[4 * i + 2] * imgW, boxes[4 * i + 3] * imgH, s,
                )
            )
        }
        return Nms.run(cand, iou)
    }
}

object ScrfdDecoder {
    val STRIDES = intArrayOf(8, 16, 32)

    /**
     * @param outs 9 array datar dalam urutan output model: skor[8,16,32], bbox[8,16,32], kps[8,16,32]
     * @param inputSize sisi input persegi (640)
     * @param scale rasio resize gambar asli → input (input = asli * scale)
     * Hasil dalam koordinat gambar asli.
     */
    fun decode(outs: Array<FloatArray>, inputSize: Int, scale: Float, scoreThr: Float = 0.5f, iou: Float = 0.4f): List<FaceBox> {
        val cand = ArrayList<FaceBox>()
        for (li in STRIDES.indices) {
            val stride = STRIDES[li]
            val scores = outs[li]; val bbox = outs[li + 3]; val kps = outs[li + 6]
            val g = inputSize / stride
            for (idx in scores.indices) {
                val s = scores[idx]
                if (s < scoreThr) continue
                val cell = idx / 2                      // 2 anchor per sel
                val cx = ((cell % g) * stride).toFloat()
                val cy = ((cell / g) * stride).toFloat()
                val l = bbox[4 * idx] * stride; val t = bbox[4 * idx + 1] * stride
                val r = bbox[4 * idx + 2] * stride; val b = bbox[4 * idx + 3] * stride
                val lm = FloatArray(10)
                for (k in 0 until 5) {
                    lm[2 * k] = (kps[10 * idx + 2 * k] * stride + cx) / scale
                    lm[2 * k + 1] = (kps[10 * idx + 2 * k + 1] * stride + cy) / scale
                }
                cand.add(FaceBox((cx - l) / scale, (cy - t) / scale, (cx + r) / scale, (cy + b) / scale, s, lm))
            }
        }
        return Nms.run(cand, iou)
    }
}
