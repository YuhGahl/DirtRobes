#!/usr/bin/env python3
"""
Image d'aperçu d'un modèle Bedrock (.geo.json + texture), vue de 3/4 avant, fond transparent.
Rendu logiciel simple (z-buffer, éclairage par face), avec les rotations des os et des cubes.

  python3 tools/models/render_preview.py <modele.geo.json> <texture.png> <sortie.png> [taille]
  python3 tools/models/render_preview.py --all docs/renders   (tous les Spectrobes qui ont un modèle à eux)
"""
import glob, json, math, os, sys
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")


def R(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X


def faces(frm, to, uv, mirror):
    x0, y0, z0 = frm; x1, y1, z1 = to
    w, h, d = (abs(to[i] - frm[i]) for i in range(3))
    u, v = uv
    F = {"north": ([(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], (u + d, v + d, w, h)),
         "south": ([(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], (u + 2 * d + w, v + d, w, h)),
         "east": ([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], (u, v + d, d, h)),
         "west": ([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], (u + d + w, v + d, d, h)),
         "up": ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (u + d, v, w, d)),
         "down": ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], (u + d + w, v, w, d))}
    if mirror:
        F["east"], F["west"] = (F["east"][0], F["west"][1]), (F["west"][0], F["east"][1])
    return F


def quads(geo):
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}

    def chain(name):
        out = []
        while name:
            b = bones[name]
            out.append(b)
            name = b.get("parent")
        return out  # du plus proche au plus lointain

    res = []
    for b in g["bones"]:
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            inf = c.get("inflate", 0)
            frm = np.array([-(o[0] + s[0]) - inf, o[1] - inf, o[2] - inf]); to = np.array([-o[0] + inf, o[1] + s[1] + inf, o[2] + s[2] + inf])
            uv = c.get("uv", [0, 0])
            if isinstance(uv, dict):
                continue
            for name, (pts, rect) in faces(frm, to, uv, c.get("mirror")).items():
                P = [np.array(p, float) for p in pts]
                if c.get("rotation"):
                    pv = np.array([-c["pivot"][0], c["pivot"][1], c["pivot"][2]])
                    r = c["rotation"]; M = R(-r[0], -r[1], r[2])
                    P = [M @ (p - pv) + pv for p in P]
                for bb in chain(b["name"]):
                    r = bb.get("rotation")
                    if r and any(r):
                        pv = np.array([-bb["pivot"][0], bb["pivot"][1], bb["pivot"][2]])
                        M = R(-r[0], -r[1], r[2])
                        P = [M @ (p - pv) + pv for p in P]
                res.append((name, P, rect))
    return res, (g["description"]["texture_width"], g["description"]["texture_height"])


def render(geo, texpath, out, size=512, yaw=215, pitch=-22):
    Q, (tw, th) = quads(geo)
    tex = np.array(Image.open(texpath).convert("RGBA")).astype(float)
    sx, sy = tex.shape[1] / tw, tex.shape[0] / th
    V = R(pitch, 0, 0) @ R(0, yaw, 0)
    pts = np.array([V @ p for _, P, _ in Q for p in P])
    mn, mx = pts.min(0), pts.max(0)
    sc = (size * 0.9) / max(mx[0] - mn[0], mx[1] - mn[1])
    ox = (size - (mx[0] - mn[0]) * sc) / 2; oy = (size - (mx[1] - mn[1]) * sc) / 2
    img = np.zeros((size, size, 4)); zb = np.full((size, size), -1e9)
    light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
    for name, P, (fu, fv, fw, fh) in Q:
        W = [V @ p for p in P]
        S = [((w[0] - mn[0]) * sc + ox, size - ((w[1] - mn[1]) * sc + oy), w[2]) for w in W]
        n = np.cross(P[1] - P[0], P[3] - P[0]); ln = np.linalg.norm(n)
        shade = 0.55 + 0.45 * abs(np.dot(n / ln, light)) if ln > 1e-9 else 0.8
        uvs = [(fu, fv), (fu + fw, fv), (fu + fw, fv + fh), (fu, fv + fh)]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (S[i] for i in tri); ua, ub, uc = (uvs[i] for i in tri)
            x0, x1 = max(0, int(min(a[0], b[0], c[0]))), min(size - 1, int(max(a[0], b[0], c[0])) + 1)
            y0, y1 = max(0, int(min(a[1], b[1], c[1]))), min(size - 1, int(max(a[1], b[1], c[1])) + 1)
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9 or x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l1 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
            l2 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
            l3 = 1 - l1 - l2
            m = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
            if not m.any():
                continue
            z = l1 * a[2] + l2 * b[2] + l3 * c[2]
            U = np.clip(((l1 * ua[0] + l2 * ub[0] + l3 * uc[0]) * sx).astype(int), 0, tex.shape[1] - 1)
            Vv = np.clip(((l1 * ua[1] + l2 * ub[1] + l3 * uc[1]) * sy).astype(int), 0, tex.shape[0] - 1)
            col = tex[Vv, U]
            m &= col[..., 3] > 200  # l'aperçu ignore les parties semi-transparentes (auras)
            yy, xx = np.nonzero(m)
            Y, X = yy + y0, xx + x0
            better = z[yy, xx] > zb[Y, X]
            zb[Y[better], X[better]] = z[yy, xx][better]
            img[Y[better], X[better], :3] = col[yy[better], xx[better], :3] * shade
            img[Y[better], X[better], 3] = 255
    Image.fromarray(img.astype(np.uint8)).save(out)


def all_species(outdir):
    os.makedirs(outdir, exist_ok=True)
    done = []
    for rf in sorted(glob.glob(os.path.join(A, "bedrock/spectrobes/resolvers/*.json"))):
        r = json.load(open(rf))
        sid = r.get("species", "").split(":")[-1]
        model = r.get("model", "").split(":")[-1]
        if not sid or model.startswith("placeholder") or not r.get("texture"):
            continue
        gp = os.path.join(A, "bedrock/pokemon/models", model + ".geo.json")
        tp = os.path.join(A, r["texture"].split(":")[-1])
        if os.path.exists(gp) and os.path.exists(tp):
            render(json.load(open(gp)), tp, os.path.join(outdir, sid + ".png"))
            done.append(sid)
    print(len(done), "aperçus :", " ".join(done))


if __name__ == "__main__":
    if sys.argv[1] == "--all":
        all_species(sys.argv[2])
    else:
        render(json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 512)
