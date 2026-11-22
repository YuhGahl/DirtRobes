#!/usr/bin/env python3
"""
Ombrage automatique d'une texture de Spectrobe, d'après les faces du modèle (UV par face) :
dessus plus clair, dessous plus sombre, côtés en léger dégradé de haut en bas, faces intérieures des pattes
plus sombres, pieds dans une autre teinte. Les noirs, blancs et gris (yeux, contours) ne sont pas touchés.

  python3 tools/models/shade_texture.py <modèle.geo.json> <texture.png> <sortie.png> [--feet=#RRGGBB]
"""
import colorsys, json, math, sys
from PIL import Image

UP, DOWN = 1.12, 0.72          # dessus / dessous
SIDE_TOP, SIDE_BOTTOM = 1.06, 0.86
INNER = 0.82                   # faces intérieures des pattes


def main(geo_path, tex_path, out_path, feet=None):
    g = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    W, H = g["description"]["texture_width"], g["description"]["texture_height"]
    src = Image.open(tex_path).convert("RGBA")
    out = src.copy()
    sp, op = src.load(), out.load()
    sx, sy = src.width / W, src.height / H
    done = set()
    feet_rgb = tuple(int(feet[i:i + 2], 16) for i in (1, 3, 5)) if feet else None
    for bone in g["bones"]:
        leg = "leg" in bone["name"]
        for c in bone.get("cubes", []):
            uv = c.get("uv")
            if not isinstance(uv, dict):
                continue
            is_foot = leg and c["size"][1] <= 0.45  # semelles plates
            cx = c["origin"][0] + c["size"][0] / 2
            inner = "west" if cx < 0 else "east"    # face tournée vers le centre du corps
            for face, f in uv.items():
                fu, fv = f["uv"]
                fw, fh = f.get("uv_size", [0, 0])
                x0, x1 = sorted((fu, fu + fw))
                y0, y1 = sorted((fv, fv + fh))
                px0, px1 = math.floor(x0 * sx), max(math.ceil(x1 * sx), math.floor(x0 * sx) + 1)
                py0, py1 = math.floor(y0 * sy), max(math.ceil(y1 * sy), math.floor(y0 * sy) + 1)
                for x in range(px0, px1):
                    for y in range(py0, py1):
                        if (x, y) in done or not (0 <= x < src.width and 0 <= y < src.height):
                            continue
                        done.add((x, y))
                        r, gr, b, a = sp[x, y]
                        if a == 0:
                            continue
                        h, s, v = colorsys.rgb_to_hsv(r / 255, gr / 255, b / 255)
                        if s < 0.12 and (v < 0.2 or v > 0.9):
                            continue  # noirs et blancs : yeux, contours
                        if is_foot and feet_rgb:
                            fh_, fs, fv_ = colorsys.rgb_to_hsv(*(k / 255 for k in feet_rgb))
                            h, s, v = fh_, fs, fv_ * (0.85 + 0.3 * v)
                        if face == "up":
                            k = UP
                        elif face == "down":
                            k = DOWN
                        else:
                            # dégradé : v0 (haut de la face) → v0 + taille (bas)
                            t = ((y + 0.5) / sy - fv) / fh if fh else 0.5
                            k = SIDE_TOP + (SIDE_BOTTOM - SIDE_TOP) * min(1, max(0, t))
                            if leg and face == inner:
                                k *= INNER
                        nr, ng, nb = colorsys.hsv_to_rgb(h, s, min(1, v * k))
                        op[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    out.save(out_path)
    print("ombrage :", out_path)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    feet = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--feet=")), None)
    main(*args, feet=feet)
