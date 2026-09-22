#!/usr/bin/env bash
# OPSIONAL — memasang profil "InsightFace" (SCRFD-500M + ArcFace MobileFaceNet 512-d).
# PERINGATAN LISENSI: bobot model InsightFace hanya untuk RISET NON-KOMERSIAL
# (lihat README deepinsight/insightface, bagian License). Jangan dipakai untuk produk komersial.
set -euo pipefail
cd "$(dirname "$0")/.."
DEST=app/src/main/assets/models
TMP="$(mktemp -d)"
curl -L --fail -o "$TMP/buffalo_sc.zip" https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_sc.zip
unzip -o -q "$TMP/buffalo_sc.zip" det_500m.onnx w600k_mbf.onnx -d "$TMP"
mkdir -p "$DEST"
cp "$TMP/det_500m.onnx" "$TMP/w600k_mbf.onnx" "$DEST/"
echo "OK. Build ulang, lalu pilih profil InsightFace di Pengaturan dan jalankan 'Sinkron & index' di Collections."
sha256sum "$DEST/det_500m.onnx" "$DEST/w600k_mbf.onnx" || true
echo "harapan: det_500m=5e4447f5...ea3a  w600k_mbf=9cc6e4a7...eb4f"
