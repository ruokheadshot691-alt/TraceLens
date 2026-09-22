"""Referensi independen (numpy) untuk unit test core Kotlin. Mencetak konstanta yang ditempel ke CoreTest.kt"""
import numpy as np, math, sys
sys.path.insert(0,'.')

def pix(x,y): return ((x*5+y*3)%256, (x*y)%256, (x*x+y)%256)   # r,g,b

def build_thumb(w,h,n=128):
    if w>=n and h>=n:
        cnt=np.zeros(n*n,np.int64); sg=np.zeros(n*n,np.int64); sr=sg.copy(); sgc=sg.copy(); sb=sg.copy()
        for y in range(h):
            by=y*n//h
            for x in range(w):
                bx=x*n//w; r,g,b=pix(x,y); i=by*n+bx
                cnt[i]+=1; sg[i]+=(77*r+150*g+29*b)>>8; sr[i]+=r; sgc[i]+=g; sb[i]+=b
        gray=sg/cnt; rs=sr/cnt; gs=sgc/cnt; bs=sb/cnt
    else:
        gray=np.zeros(n*n); rs=gray.copy(); gs=gray.copy(); bs=gray.copy()
        for by in range(n):
            for bx in range(n):
                sx=min(w-1,bx*w//n); sy=min(h-1,by*h//n); r,g,b=pix(sx,sy); i=by*n+bx
                gray[i]=(77*r+150*g+29*b)>>8; rs[i]=r; gs[i]=g; bs[i]=b
    g4=4; cell=n//g4; color=[]
    for cy in range(g4):
        for cx in range(g4):
            rr=gg=bb=0.0
            for yy in range(cell):
                for xx in range(cell):
                    i=(cy*cell+yy)*n+cx*cell+xx; rr+=rs[i]; gg+=gs[i]; bb+=bs[i]
            d=cell*cell
            for v in (rr,gg,bb): color.append(int(min(255,max(0,math.floor(v/d+0.5)))))
    return gray,color

def area_resize(src,sw,x0,y0,x1,y1,dw,dh):
    rw=x1-x0; rh=y1-y0; fx=rw/dw; fy=rh/dh
    out=np.zeros(dw*dh)
    for dy in range(dh):
        ya=y0+dy*fy; yb=y0+(dy+1)*fy
        for dx in range(dw):
            xa=x0+dx*fx; xb=x0+(dx+1)*fx
            acc=0.0; ws=0.0; iy=math.floor(ya)
            while iy<yb:
                wy=min(yb,iy+1.0)-max(ya,float(iy))
                if wy>0:
                    ix=math.floor(xa)
                    while ix<xb:
                        wx=min(xb,ix+1.0)-max(xa,float(ix))
                        if wx>0: acc+=src[iy*sw+ix]*wx*wy; ws+=wx*wy
                        ix+=1
                iy+=1
            out[dy*dw+dx]=acc/ws
    return out

C8=np.array([[math.cos((2*x+1)*u*math.pi/64.0) for x in range(32)] for u in range(8)])
def phash(g32):
    m=g32.reshape(32,32); low=(C8@m@C8.T).flatten()
    s=np.sort(low); med=(s[31]+s[32])/2.0
    h=0
    for v in low: h=(h<<1)|(1 if v>med else 0)
    return h
def dhash(g72):
    h=0
    for r in range(8):
        for c in range(8): h=(h<<1)|(1 if g72[r*9+c+1]>g72[r*9+c] else 0)
    return h
WINS=[(0.0,0.0,1.0,1.0)]
for f in (0.9,0.8,0.7,0.6):
    for oy in (0.0,0.5,1.0):
        for ox in (0.0,0.5,1.0):
            x0=(1-f)*ox; y0=(1-f)*oy; WINS.append((x0,y0,x0+f,y0+f))
def signature(gray):
    out=[]
    for w in WINS:
        x0,y0,x1,y1=[int(math.floor(v*128+0.5)) for v in w]
        out.append((phash(area_resize(gray,128,x0,y0,x1,y1,32,32)), dhash(area_resize(gray,128,x0,y0,x1,y1,9,8))))
    return out

print("WINDOW_COUNT",len(WINS))
for (w,h) in [(200,150),(100,90)]:
    gray,color=build_thumb(w,h)
    sig=signature(gray)
    print(f"--- image {w}x{h}: graySum={gray.sum():.6f}")
    print("color=",color)
    for i in (0,1,5,20,36):
        print(f"  win{i}: p={sig[i][0]:016x} d={sig[i][1]:016x}")

# ---- similarity transform (Umeyama, SVD) pada 5 titik contoh
from proto import umeyama, ARC
kps=np.array([[120.5,80.2],[170.1,78.9],[146.0,110.3],[128.7,140.8],[166.2,139.5]],np.float64)
M=umeyama(kps,ARC.astype(np.float64)); print("SIM",[round(float(v),5) for v in M.flatten()])
kps2=np.array([[33.0,41.0],[60.0,35.0],[50.0,60.0],[38.0,75.0],[62.0,70.0]],np.float64)
M=umeyama(kps2,ARC.astype(np.float64)); print("SIM2",[round(float(v),5) for v in M.flatten()])

# ---- SCRFD decode reference (input 64, scale 0.5)
def scrfd_ref(outs,size,scale,thr=0.5,iou=0.4):
    B=[];S=[];K=[]
    for i,stride in enumerate([8,16,32]):
        sc=outs[i]; bp=outs[i+3].reshape(-1,4)*stride; kp=outs[i+6].reshape(-1,10)*stride
        g=size//stride; ys,xs=np.mgrid[:g,:g]
        c=np.stack([xs,ys],-1).astype(np.float64).reshape(-1,2)*stride; c=np.repeat(c,2,axis=0)
        pos=np.where(sc>=thr)[0]; cc=c[pos]; d=bp[pos]
        B.append(np.stack([cc[:,0]-d[:,0],cc[:,1]-d[:,1],cc[:,0]+d[:,2],cc[:,1]+d[:,3]],1)); S.append(sc[pos]); K.append(kp[pos].reshape(-1,5,2)+cc[:,None,:])
    B=np.concatenate(B)/scale;S=np.concatenate(S);K=np.concatenate(K)/scale
    from proto import nms
    keep=nms(B,S,iou); return B[keep],S[keep],K[keep]
size=64; outs=[]
for stride in (8,16,32): outs.append(np.array([((i*37+stride)%101)/100.0 for i in range((size//stride)**2*2)]))
for stride in (8,16,32): outs.append(np.array([((i*13+7)%17)/10.0 for i in range((size//stride)**2*2*4)]))
for stride in (8,16,32): outs.append(np.array([(((i*11+3)%23)-11)/10.0 for i in range((size//stride)**2*2*10)]))
B,S,K=scrfd_ref(outs,size,0.5)
print("SCRFD n=",len(B))
for b,s,k in zip(B,S,K): print(" box",[round(float(v),3) for v in b],"score",round(float(s),3),"kp0",[round(float(v),3) for v in k[0]])
