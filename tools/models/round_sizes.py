#!/usr/bin/env python3
"""
Arrondit les tailles des cubes d'un .bbmodel à la demi-unité (minimum 0,5) pour des pixels carrés et sans
débordement dans Cobblemon. Le centre de chaque cube est gardé ; pour les cubes très fins (yeux, sourcils,
plaques), c'est la face extérieure qui reste en place et le cube s'épaissit vers l'intérieur (invisible).
Les UV par face sont gardées telles quelles (les cubes en « Box UV » passent en UV par face pour ne pas
être recalculés par Blockbench).

  python3 tools/models/round_sizes.py entrée.bbmodel sortie.bbmodel
"""
import json, sys

STEP = 0.5


def rnd(x):
    return max(STEP, round(x / STEP) * STEP)


def main(src, dst):
    bb = json.load(open(src, encoding="utf-8"))
    changed = 0
    for e in bb["elements"]:
        if e.get("type", "cube") != "cube":
            continue
        f, t = e["from"][:], e["to"][:]
        for i in range(3):
            size = t[i] - f[i]
            new = rnd(size)
            if abs(new - size) < 1e-6:
                continue
            changed += 1
            if size < STEP:
                # garder la face extérieure (la plus loin du centre du modèle)
                if abs(f[i]) > abs(t[i]):
                    t[i] = f[i] + new
                else:
                    f[i] = t[i] - new
            else:
                c = (f[i] + t[i]) / 2
                f[i], t[i] = c - new / 2, c + new / 2
        e["from"], e["to"] = [round(v, 4) for v in f], [round(v, 4) for v in t]
        if e.get("box_uv"):
            e["box_uv"] = False
    bb["meta"]["box_uv"] = False
    json.dump(bb, open(dst, "w", encoding="utf-8"))
    print(f"{changed} dimensions arrondies → {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
