from functools import lru_cache
def line_solve(clue, cells):
    """cells: list of -1 unknown, 0 empty, 1 filled. returns new list or None if contradiction"""
    n=len(cells); k=len(clue)
    # suffix feasibility via DP over positions
    cl=tuple(clue); cs=tuple(cells)
    can0=[False]*n; can1=[False]*n
    memo={}
    def ok(i,j):
        # can place blocks j.. starting at pos >= i
        key=(i,j)
        if key in memo: return memo[key]
        if j==k:
            r=all(cs[p]!=1 for p in range(i,n)); memo[key]=r; return r
        L=cl[j]; r=False
        p=i
        while p+L<=n:
            if all(cs[q]!=0 for q in range(p,p+L)) and (p+L==n or cs[p+L]!=1):
                nxt=p+L+1
                if ok(min(nxt,n) if nxt<=n else n, j+1) if p+L<n else (j+1==k):
                    r=True;break
            if cs[p]==1: break
            p+=1
        memo[key]=r;return r
    if not ok(0,0): return None
    # mark: enumerate with forward DP marking
    memo2={}
    def mark(i,j):
        if (i,j) in memo2: return memo2[(i,j)]
        res=False
        if j==k:
            if all(cs[p]!=1 for p in range(i,n)):
                for p in range(i,n): can0[p]=True
                res=True
            memo2[(i,j)]=res;return res
        L=cl[j];p=i
        while p+L<=n:
            if all(cs[q]!=0 for q in range(p,p+L)) and (p+L==n or cs[p+L]!=1):
                if p+L<n: good=mark(p+L+1,j+1)
                else: good=(j+1==k)
                if good:
                    res=True
                    for q in range(i,p): can0[q]=True
                    for q in range(p,p+L): can1[q]=True
                    if p+L<n: can0[p+L]=True
            if cs[p]==1: break
            p+=1
        memo2[(i,j)]=res;return res
    mark(0,0)
    out=list(cells)
    for p in range(n):
        if can1[p] and not can0[p]: out[p]=1
        elif can0[p] and not can1[p]: out[p]=0
    return out
def clues(line):
    r=[];c=0
    for v in line:
        if v: c+=1
        elif c: r.append(c);c=0
    if c: r.append(c)
    return r
def solve(rowc,colc,H,W):
    g=[[-1]*W for _ in range(H)];rounds=0
    while True:
        rounds+=1;changed=False
        for y in range(H):
            nl=line_solve(rowc[y],g[y])
            if nl is None: return None,rounds
            if nl!=g[y]: g[y]=nl;changed=True
        for x in range(W):
            col=[g[y][x] for y in range(H)];nl=line_solve(colc[x],col)
            if nl is None: return None,rounds
            if nl!=col:
                changed=True
                for y in range(H): g[y][x]=nl[y]
        if all(v!=-1 for row in g for v in row): return g,rounds
        if not changed: return None,rounds
