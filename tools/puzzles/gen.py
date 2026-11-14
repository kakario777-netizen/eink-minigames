import json,sys,numpy as np
from PIL import Image,ImageDraw,ImageFont
from solver import solve,clues
from emojis import LIST
font=ImageFont.truetype('NotoEmoji.ttf',400)
try: font.set_variation_by_axes([700])
except Exception as e: print(e)
def render(ch):
    im=Image.new('L',(560,560),255);d=ImageDraw.Draw(im)
    d.text((280,280),ch,font=font,fill=0,anchor='mm')
    a=255-np.array(im);ys,xs=np.where(a>128)
    if len(ys)==0: return None
    a=a[ys.min():ys.max()+1,xs.min():xs.max()+1]
    h,w=a.shape;s=max(h,w);pad=np.zeros((s,s),np.uint8)
    pad[(s-h)//2:(s-h)//2+h,(s-w)//2:(s-w)//2+w]=a
    return pad
def grid(img,N,th):
    g=np.array(Image.fromarray(img).resize((N,N),Image.BOX))/255.
    return (g>th).astype(int)
def check(g):
    H,W=g.shape
    if g.sum()==0 or (g.sum(1)==0).all(): return None
    r=[clues(g[y]) for y in range(H)];c=[clues(g[:,x]) for x in range(W)]
    s,rounds=solve(r,c,H,W)
    if s is None: return None
    return rounds
SIZES=json.loads(sys.argv[1]);OUT=sys.argv[2]
out=[]
for idx,(ch,name,cat) in enumerate(LIST):
    img=render(ch)
    if img is None: continue
    for lv,N in SIZES.items():
        best=None
        for th in (0.35,0.3,0.4,0.25,0.45,0.5,0.2):
            g=grid(img,N,th);fill=g.mean()
            if not 0.25<=fill<=0.7: continue
            # trim fully empty border rows/cols? keep square
            r=check(g)
            if r is not None: best=(g,r,th);break
        if best:
            g,r,th=best
            out.append(dict(title=name,cat=cat,level=lv,n=N,rounds=r,fill=round(float(g.mean()),2),rows=[''.join(map(str,row)) for row in g]))
    print(idx,name,[o['level'] for o in out if o['title']==name],file=sys.stderr)
json.dump(out,open(OUT,'w'),ensure_ascii=False)
