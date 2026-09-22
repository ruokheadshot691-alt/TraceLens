"""Prototype referensi (Python) untuk pipeline TraceLens.
Dipakai untuk memvalidasi model + matematika sebelum diport ke Kotlin.
"""
import numpy as np, cv2, onnxruntime as ort

ort.set_default_logger_severity(4)
M = "/home/claude/models_probe/"


def sess(path):
    return ort.InferenceSession(path, providers=["CPUExecutionProvider"])


# ---------------- NMS ----------------
def nms(boxes, scores, iou_thr):
    idx = np.argsort(-scores)
    keep = []
    while len(idx):
        i = idx[0]
        keep.append(i)
        if len(idx) == 1:
            break
        rest = idx[1:]
        xx1 = np.maximum(boxes[i, 0], boxes[rest, 0])
        yy1 = np.maximum(boxes[i, 1], boxes[rest, 1])
        xx2 = np.minimum(boxes[i, 2], boxes[rest, 2])
        yy2 = np.minimum(boxes[i, 3], boxes[rest, 3])
        inter = np.maximum(0, xx2 - xx1) * np.maximum(0, yy2 - yy1)
        a = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
        b = (boxes[rest, 2] - boxes[rest, 0]) * (boxes[rest, 3] - boxes[rest, 1])
        iou = inter / (a + b - inter + 1e-9)
        idx = rest[iou <= iou_thr]
    return keep


# ---------------- Ultra-Light RFB ----------------
class Rfb:
    def __init__(self, path, w, h):
        self.s, self.w, self.h = sess(path), w, h

    def detect(self, bgr, thr=0.7):
        H, W = bgr.shape[:2]
        im = cv2.resize(bgr, (self.w, self.h))
        im = cv2.cvtColor(im, cv2.COLOR_BGR2RGB).astype(np.float32)
        im = (im - 127.0) / 128.0
        im = im.transpose(2, 0, 1)[None]
        scores, boxes = self.s.run(None, {"input": im})
        sc = scores[0, :, 1]
        m = sc > thr
        b = boxes[0][m] * np.array([W, H, W, H], np.float32)
        s = sc[m]
        k = nms(b, s, 0.3)
        return b[k], s[k]


# ---------------- SCRFD ----------------
class Scrfd:
    def __init__(self, path, size=640):
        self.s, self.size = sess(path), size

    def detect(self, bgr, thr=0.5):
        H, W = bgr.shape[:2]
        ratio = H / W
        if ratio > 1:
            nh, nw = self.size, int(self.size / ratio)
        else:
            nw, nh = self.size, int(self.size * ratio)
        sc_ = nh / H
        r = cv2.resize(bgr, (nw, nh))
        canvas = np.zeros((self.size, self.size, 3), np.uint8)
        canvas[:nh, :nw] = r
        blob = cv2.dnn.blobFromImage(canvas, 1.0 / 128, (self.size, self.size), (127.5, 127.5, 127.5), swapRB=True)
        outs = self.s.run(None, {self.s.get_inputs()[0].name: blob})
        B, S, K = [], [], []
        for i, stride in enumerate([8, 16, 32]):
            scores = outs[i][:, 0]
            bp = outs[i + 3] * stride
            kp = outs[i + 6] * stride
            g = self.size // stride
            ys, xs = np.mgrid[:g, :g]
            centers = np.stack([xs, ys], -1).astype(np.float32).reshape(-1, 2) * stride
            centers = np.repeat(centers, 2, axis=0)  # 2 anchors per cell
            pos = np.where(scores >= thr)[0]
            c = centers[pos]
            d = bp[pos]
            box = np.stack([c[:, 0] - d[:, 0], c[:, 1] - d[:, 1], c[:, 0] + d[:, 2], c[:, 1] + d[:, 3]], 1)
            kk = kp[pos].reshape(-1, 5, 2) + c[:, None, :]
            B.append(box); S.append(scores[pos]); K.append(kk)
        B = np.concatenate(B) / sc_; S = np.concatenate(S); K = np.concatenate(K) / sc_
        k = nms(B, S, 0.4)
        return B[k], S[k], K[k]


# ---------------- Alignment ----------------
ARC = np.array([[38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366],
                [41.5493, 92.3655], [70.7299, 92.2041]], np.float32)


def umeyama(src, dst):
    """Similarity transform (2x3) src->dst, tanpa reflection."""
    n = src.shape[0]
    ms, md = src.mean(0), dst.mean(0)
    sc, dc = src - ms, dst - md
    var = (sc ** 2).sum() / n
    cov = dc.T @ sc / n
    U, D, Vt = np.linalg.svd(cov)
    S = np.eye(2)
    if np.linalg.det(cov) < 0:
        S[1, 1] = -1
    R = U @ S @ Vt
    scale = (D * np.diag(S)).sum() / var
    t = md - scale * R @ ms
    M = np.zeros((2, 3), np.float32)
    M[:, :2] = scale * R
    M[:, 2] = t
    return M


def align5(bgr, kps):
    M = umeyama(kps.astype(np.float64), ARC.astype(np.float64))
    return cv2.warpAffine(bgr, M, (112, 112), borderValue=0)


def crop_box(bgr, box, margin=0.15):
    """LITE: square crop di sekitar box dengan margin, resize 112."""
    x1, y1, x2, y2 = box
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    side = max(x2 - x1, y2 - y1) * (1 + 2 * margin)
    half = side / 2
    M = np.array([[112 / side, 0, 56 - cx * 112 / side], [0, 112 / side, 56 - cy * 112 / side]], np.float32)
    return cv2.warpAffine(bgr, M, (112, 112), borderMode=cv2.BORDER_REPLICATE)


# ---------------- Embedders ----------------
class MobileFaceNet192:
    def __init__(self):
        self.s = sess(M + "mfn.onnx")

    def embed(self, faces_bgr):
        out = []
        for f in faces_bgr:
            rgb = cv2.cvtColor(f, cv2.COLOR_BGR2RGB).astype(np.float32)
            x = ((rgb - 127.5) / 128.0).transpose(2, 0, 1)
            batch = np.stack([x, x])  # model batch tetap 2
            e = self.s.run(None, {"input": batch})[0][0]
            out.append(e / np.linalg.norm(e))
        return np.array(out)


class ArcFaceMbf:
    def __init__(self):
        self.s = sess(M + "buffalo_sc/w600k_mbf.onnx")

    def embed(self, faces_bgr):
        out = []
        for f in faces_bgr:
            rgb = cv2.cvtColor(f, cv2.COLOR_BGR2RGB).astype(np.float32)
            x = ((rgb - 127.5) / 127.5).transpose(2, 0, 1)[None]
            e = self.s.run(None, {self.s.get_inputs()[0].name: x})[0][0]
            out.append(e / np.linalg.norm(e))
        return np.array(out)
