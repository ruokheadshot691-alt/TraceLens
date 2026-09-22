import numpy as np, cv2
def gray_thumb(bgr, n=128):
    g=cv2.cvtColor(bgr,cv2.COLOR_BGR2GRAY).astype(np.float64)
    return cv2.resize(g,(n,n),interpolation=cv2.INTER_AREA)
_C=np.array([[np.cos((2*x+1)*u*np.pi/64) for x in range(32)] for u in range(32)])
def phash_from_gray32(g32):
    d=_C@g32@_C.T
    low=d[:8,:8].flatten()
    med=np.sort(low)[31:33].mean()
    h=0
    for v in low: h=(h<<1)|(1 if v>med else 0)
    return h
def dhash_from_gray98(g):  # 8 rows x 9 cols
    h=0
    for r in range(8):
        for c in range(8): h=(h<<1)|(1 if g[r,c+1]>g[r,c] else 0)
    return h
def hashes(thumb, win=(0,0,1,1)):
    n=thumb.shape[0]; x0,y0,x1,y1=[int(round(v*n)) for v in win]
    sub=thumb[y0:y1,x0:x1]
    p=phash_from_gray32(cv2.resize(sub,(32,32),interpolation=cv2.INTER_AREA))
    d=dhash_from_gray98(cv2.resize(sub,(9,8),interpolation=cv2.INTER_AREA))
    return p,d
WINS=[(0,0,1,1),(.15,.15,.85,.85),(0,0,.6,.6),(.4,0,1,.6),(0,.4,.6,1),(.4,.4,1,1)]
def ham(a,b): return bin(a^b).count("1")
