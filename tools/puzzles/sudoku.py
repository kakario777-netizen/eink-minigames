import random,json,sys
ROWS=[[r*9+c for c in range(9)] for r in range(9)]
COLS=[[r*9+c for r in range(9)] for c in range(9)]
BOXS=[[(br*3+r)*9+bc*3+c for r in range(3) for c in range(3)] for br in range(3) for bc in range(3)]
UNITS=ROWS+COLS+BOXS
PEERS=[set() for _ in range(81)]
for u in UNITS:
    for i in u: PEERS[i]|=set(u)
for i in range(81): PEERS[i].discard(i)
PEERS=[list(p) for p in PEERS]
def count(g,limit=2):
    g=g[:];cnt=[0]
    def cand(i): 
        used={g[p] for p in PEERS[i]};return [v for v in range(1,10) if v not in used]
    def rec():
        best=None;bc=None
        for i in range(81):
            if g[i]==0:
                c=cand(i)
                if not c: return
                if best is None or len(c)<len(bc): best,bc=i,c
                if len(c)==1: break
        if best is None: cnt[0]+=1;return
        for v in bc:
            g[best]=v;rec()
            if cnt[0]>=limit: g[best]=0;return
        g[best]=0
    rec();return cnt[0]
def full():
    g=[0]*81
    def rec(i):
        if i==81: return True
        if g[i]: return rec(i+1)
        vs=list(range(1,10));random.shuffle(vs)
        used={g[p] for p in PEERS[i]}
        for v in vs:
            if v not in used:
                g[i]=v
                if rec(i+1): return True
        g[i]=0;return False
    rec(0);return g
# logical grader: returns highest technique level used (1 singles-naked,2 hidden,3 locked candidates/pairs,4 triples/xwing) or 9 if stuck
def grade(g):
    g=g[:];C=[set() if g[i] else set(range(1,10))-{g[p] for p in PEERS[i]} for i in range(81)]
    hi=0
    def place(i,v):
        g[i]=v;C[i]=set()
        for p in PEERS[i]: C[p].discard(v)
    while 0 in g:
        if any(not C[i] for i in range(81) if g[i]==0): return 99
        done=False
        for i in range(81):
            if g[i]==0 and len(C[i])==1: place(i,next(iter(C[i])));hi=max(hi,1);done=True
        if done: continue
        for u in UNITS:
            for v in range(1,10):
                pl=[i for i in u if g[i]==0 and v in C[i]]
                if len(pl)==1: place(pl[0],v);hi=max(hi,2);done=True;break
            if done:break
        if done: continue
        # locked candidates (pointing/claiming)
        ch=False
        for b in BOXS:
            for v in range(1,10):
                pl=[i for i in b if g[i]==0 and v in C[i]]
                if len(pl)<2: continue
                for line in (ROWS[pl[0]//9],COLS[pl[0]%9]):
                    if all(i in line for i in pl):
                        for i in line:
                            if i not in b and v in C[i]: C[i].discard(v);ch=True
        for line in ROWS+COLS:
            for v in range(1,10):
                pl=[i for i in line if g[i]==0 and v in C[i]]
                if len(pl)<2: continue
                for b in BOXS:
                    if all(i in b for i in pl):
                        for i in b:
                            if i not in line and v in C[i]: C[i].discard(v);ch=True
        # naked pairs
        for u in UNITS:
            ps=[i for i in u if len(C[i])==2]
            for a in range(len(ps)):
                for b2 in range(a+1,len(ps)):
                    if C[ps[a]]==C[ps[b2]]:
                        for i in u:
                            if i not in (ps[a],ps[b2]) and C[i]&C[ps[a]]: C[i]-=C[ps[a]];ch=True
        if ch: hi=max(hi,3);continue
        # naked triples + x-wing
        for u in UNITS:
            ps=[i for i in u if 2<=len(C[i])<=3]
            n=len(ps)
            for a in range(n):
                for b2 in range(a+1,n):
                    for c in range(b2+1,n):
                        un=C[ps[a]]|C[ps[b2]]|C[ps[c]]
                        if len(un)==3:
                            for i in u:
                                if i not in (ps[a],ps[b2],ps[c]) and C[i]&un: C[i]-=un;ch=True
        for v in range(1,10):
            for lines,other in ((ROWS,lambda i:i%9),(COLS,lambda i:i//9)):
                pos=[[other(i) for i in L if g[i]==0 and v in C[i]] for L in lines]
                for a in range(9):
                    if len(pos[a])!=2: continue
                    for b2 in range(a+1,9):
                        if pos[b2]==pos[a]:
                            for k in range(9):
                                if k in(a,b2): continue
                                for i in lines[k]:
                                    if other(i) in pos[a] and v in C[i] and g[i]==0: C[i].discard(v);ch=True
        if ch: hi=max(hi,4);continue
        return 9
    return hi
def make(target):
    sol=full();g=sol[:]
    order=list(range(81));random.shuffle(order)
    hist=[]
    for i in order:
        v=g[i];g[i]=0
        if count(g)!=1: g[i]=v
        else: hist.append(g[:])
    return sol,hist
def givens(g): return sum(1 for v in g if v)
random.seed(int(sys.argv[1]) if len(sys.argv)>1 else 1)
want={'easy':int(sys.argv[2]),'medium':int(sys.argv[2]),'hard':int(sys.argv[2]),'challenge':int(sys.argv[2])}
out={k:[] for k in want}
tries=0
while any(len(out[k])<want[k] for k in want) and tries<5000:
    tries+=1
    sol,hist=make(0)
    if not hist: continue
    s=''.join(map(str,sol))
    if len(out['easy'])<want['easy']:
        c=[h for h in hist if 36<=givens(h)<=40 and grade(h)<=2]
        if c: out['easy'].append(dict(p=''.join(map(str,c[-1])),s=s,g=givens(c[-1])));continue
    if len(out['medium'])<want['medium']:
        c=[h for h in hist if 28<=givens(h)<=33 and grade(h)==2]
        if c: out['medium'].append(dict(p=''.join(map(str,c[-1])),s=s,g=givens(c[-1])));continue
    m=hist[-1];gr=grade(m)
    if gr==3 and len(out['hard'])<want['hard']: out['hard'].append(dict(p=''.join(map(str,m)),s=s,g=givens(m)))
    elif gr>=4 and len(out['challenge'])<want['challenge']: out['challenge'].append(dict(p=''.join(map(str,m)),s=s,g=givens(m)))
print({k:len(v) for k,v in out.items()},tries,file=sys.stderr)
json.dump(out,open(f'sud_{sys.argv[1]}.json','w'))
