#!/usr/bin/env bash
# Cara model app/src/main/assets/models/mobilefacenet_192.onnx dibuat (reproduksi):
#   TFLite MobileFaceNet dari syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing  ->  ONNX via tflite2onnx.
# Model sumber punya batch TETAP 2 (input [2,112,112,3]); hasil ONNX: input [2,3,112,112], output [2,192].
set -euo pipefail
pip install --break-system-packages tflite2onnx onnxruntime ai-edge-litert numpy
curl -L --fail -o MobileFaceNet.tflite \
  https://raw.githubusercontent.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing/master/app/src/main/assets/MobileFaceNet.tflite
python3 - <<'PY'
import tflite2onnx, numpy as np, onnxruntime as ort
from ai_edge_litert.interpreter import Interpreter
tflite2onnx.convert('MobileFaceNet.tflite', 'mobilefacenet_192.onnx')
it = Interpreter(model_path='MobileFaceNet.tflite'); it.allocate_tensors()
x = np.random.default_rng(0).uniform(-1, 1, (2, 112, 112, 3)).astype(np.float32)
it.set_tensor(it.get_input_details()[0]['index'], x); it.invoke()
t = it.get_tensor(it.get_output_details()[0]['index'])
s = ort.InferenceSession('mobilefacenet_192.onnx', providers=['CPUExecutionProvider'])
o = s.run(None, {s.get_inputs()[0].name: x.transpose(0, 3, 1, 2)})[0]
print('max abs diff tflite vs onnx:', float(np.abs(t - o).max()))   # terukur: 2.6e-05
PY
