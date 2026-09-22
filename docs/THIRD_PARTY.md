# Third-party components — TraceLens

Semua model & library di bawah dicek: lisensi kode, lisensi bobot model, dan kompatibilitas Android/ARM64.

## Model bawaan (assets/models, profil "Lite" — default)

| Komponen | Sumber | Lisensi kode | Catatan lisensi data/bobot |
|---|---|---|---|
| Face detector: RFB-640 | Linzaer/Ultra-Light-Fast-Generic-Face-Detector-1MB | MIT | Dilatih di WIDER FACE (riset, umum dipakai komersial+riset; cek term WIDER FACE bila redistribusi dataset). |
| Face embedder: MobileFaceNet-192 | syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing (TFLite asli) | MIT | Arsitektur dari sirius-ai/MobileFaceNet_TF (Apache-2.0), dilatih dari data publik wajah (CASIA-WebFace/MS-Celeb turunan). Model dikonversi TFLite→ONNX dengan `tools/convert_mobilefacenet.sh`, hasil numerik identik (max abs diff 2.6e-5, diverifikasi terhadap model asli). |

Kedua model dibundel di `app/src/main/assets/models/` dan sepenuhnya offline.

## Model opsional (profil "InsightFace" — TIDAK dibundel)

| Komponen | Sumber | Lisensi kode | Catatan lisensi bobot |
|---|---|---|---|
| Face detector: SCRFD-500M | deepinsight/insightface (buffalo_sc) | MIT (kode) | **Bobot model: riset non-komersial saja** — lihat README repo, bagian License. |
| Face embedder: ArcFace MobileFaceNet w600k | deepinsight/insightface (buffalo_sc) | MIT (kode) | sama seperti di atas. |

Karena batasan lisensi bobot, profil ini **tidak** ikut di-bundle ke APK. Pasang manual (opsional, tanggung jawab pemasang) lewat `tools/fetch_insightface.sh`, hanya untuk build riset/internal — jangan dipakai di build yang didistribusikan komersial.

## OCR

| Komponen | Sumber | Lisensi |
|---|---|---|
| Mesin OCR | adaptech-cz/Tesseract4Android | Apache-2.0 |
| Data bahasa (eng, ind) | tesseract-ocr/tessdata_fast | Apache-2.0 |

## Library Android/Kotlin (via Gradle, lihat app/build.gradle.kts)

| Library | Lisensi |
|---|---|
| ONNX Runtime Mobile (com.microsoft.onnxruntime:onnxruntime-android) | MIT |
| AndroidX (core, lifecycle, activity, navigation, compose, exifinterface, room, work) | Apache-2.0 |
| Coil (io.coil-kt:coil-compose) | Apache-2.0 |
| Kotlin stdlib & coroutines | Apache-2.0 |

## Yang sengaja TIDAK dipakai

- **dlib / Python face_recognition** — berat untuk mobile, tidak ada build ARM64 resmi yang ringan. PRD memang mengecualikan ini; diganti model mobile ONNX di atas.
- **MediaPipe Face Detection (BlazeFace)** — dicoba, tapi model .tflite di repo memakai Git LFS pointer sehingga tidak bisa diverifikasi otomatis di lingkungan build ini; RFB-640 dipilih karena file model langsung ter-commit (bukan LFS) dan sudah diverifikasi jalan end-to-end.
- **opencv_zoo (YuNet/SFace)** — sama, model di-track lewat Git LFS, tidak bisa diambil dari environment tanpa akses `media.githubusercontent.com`. Bisa ditambahkan manual kalau lisensi & aksesnya dicek ulang oleh yang build.

## Cara verifikasi ulang

- `tools/run_core_tests.sh` — unit test matematika inti (hash gambar, geometri wajah, decoder detektor) terhadap referensi Python independen (numpy), lihat `tools/ref_gen.py`.
- `tools/reference_pipeline.py` / `tools/hashref.py` — prototipe Python yang dipakai untuk memvalidasi pemilihan model & ambang similarity sebelum di-port ke Kotlin.
- `tools/convert_mobilefacenet.sh` — reproduksi konversi TFLite→ONNX untuk MobileFaceNet.
