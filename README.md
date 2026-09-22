# TraceLens

Aplikasi Android (Kotlin + Jetpack Compose) untuk mencari kemiripan wajah dan gambar dari foto — **sepenuhnya offline**. Sesuai `TRACE LENS — APK PRD` yang diberikan.

## Status

Seluruh source ditulis lengkap (41 file Kotlin). **Belum pernah dikompilasi ke APK di lingkungan ini** — sandbox penulisan ini tidak punya Android SDK/Gradle dengan akses internet penuh (lihat "Cara build" di bawah). Yang sudah diverifikasi di sini tanpa Android SDK:

- Lapisan matematika inti (`app/src/main/java/com/tracelens/app/core/`) lulus 13 unit test terhadap referensi Python independen — jalankan `tools/run_core_tests.sh`.
- Model ONNX (`app/src/main/assets/models/`) diuji jalan end-to-end dengan ONNX Runtime Python: deteksi wajah, alignment, embedding, separasi same-person vs different-person, dan ketahanan terhadap flip/brightness/blur/JPEG/scale — lihat `tools/reference_pipeline.py`.
- Strategi image-hash (37 jendela crop) diuji recall/false-positive dengan crop acak — 99% recall, 0% false-positive pada ambang 16 bit.
- Semua 41 file `.kt` lulus pemeriksaan sintaks kotlinc (error yang tersisa hanya "unresolved reference" ke AndroidX/library yang memang tidak ada di sandbox non-Android ini — bukan error sintaks).

Build APK sesungguhnya (Gradle + Android SDK + AGP) silakan dijalankan di mesin kamu sendiri.

## Cara build

1. Buka folder ini di Android Studio (Koala+) — akan otomatis sync Gradle.
2. Atau CLI: `./gradlew assembleDebug` (butuh Android SDK terpasang / `local.properties` berisi `sdk.dir`).
3. `./gradlew testDebugUnitTest` untuk menjalankan seluruh unit test termasuk `CoreTest.kt`.

## Struktur

```
app/src/main/java/com/tracelens/app/
  core/     Matematika murni: image-hash (pHash/dHash+37 jendela), geometri wajah (NMS, alignment, crop), decoder RFB/SCRFD. Tanpa dependensi Android — bisa dites tanpa emulator.
  ml/       ONNX Runtime: FaceDetector, FaceEmbedder, FaceEngine (pipeline), ModelProfile (Lite/InsightFace), ImageIo (decode+EXIF-rotate).
  data/     Room: Collection/Image/Face entity, DAO, Prefs.
  index/    SAF folder scanner, Indexer (resumable), IndexWorker (WorkManager).
  search/   FaceSearcher, ImageSearcher (brute-force cosine / hash-compare berhalaman, top-K).
  ocr/      Tesseract4Android wrapper.
  meta/     Pembaca EXIF/metadata.
  web/      Modul provider sumber publik (modular, hanya buka browser — lihat PRD §9).
  export/   Export hasil ke CSV/JSON.
  ui/       Layar Compose (Home, Face Search, Image Search, Analyze, Collections, Compare, Settings) + ViewModel.
tools/      Skrip verifikasi & konversi model (Python/bash) — dipakai saat menulis app ini, tidak dibundel ke APK.
docs/THIRD_PARTY.md   Rincian lisensi tiap model/library.
```

## Poin desain penting

- **Dua profil model face** (Pengaturan → Model wajah): **Lite** (RFB-640 + MobileFaceNet-192, MIT, dibundel) dan **InsightFace** (SCRFD + ArcFace 512-d, lisensi bobot non-komersial, tidak dibundel — pasang manual via `tools/fetch_insightface.sh`). Embedding kedua profil tidak kompatibel; setiap wajah di DB menyimpan `modelId`-nya.
- **Image hash** pakai pHash+dHash pada gambar penuh **dan** 37 jendela crop (berbagai skala & posisi) supaya crop 70–95% tetap ketemu — bukan cuma resize/kompresi/brightness (yang sudah match sempurna dengan hash gambar penuh).
- **Hasil wajah selalu berlabel "Visual Similarity" / "Possible Match"**, tidak pernah nama orang — sesuai PRD §3 dan ditegaskan lagi di layar Compare.
- **Privasi**: tidak ada izin storage/lokasi di Manifest — pemilihan folder/gambar lewat SAF (`OpenDocumentTree`/`OpenMultipleDocuments`) dan Photo Picker, sehingga hanya folder/gambar yang eksplisit dipilih user yang bisa diakses. Semua inference on-device, database lokal (`allowBackup=false`).
- **Modul web/social search** (PRD §9) hanya membuka halaman resmi (Google Images, TinEye, Bing Visual Search, Yandex) di browser lewat Intent — tidak pernah upload foto, scraping, atau bypass CAPTCHA/login/paywall.
