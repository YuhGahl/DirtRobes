#!/usr/bin/env python3
"""
Variantes de couleur d'une texture (ex. les Komainu des lignées secrètes) : on recolore la teinte principale
du Spectrobe (ici le rose-rouge du corps) vers une autre teinte, en gardant les ombres et les détails
(yeux, accents bleus…). Les autres couleurs peuvent être recolorées par une 2e règle.

  python3 tools/models/recolor_variants.py

Sorties : assets/dirtrobes/textures/spectrobes/<variante>.png + resolver DirtRobes de chaque variante
(même modèle, même poser). La texture de base n'est jamais modifiée : relancer le script après l'avoir
repeinte met les variantes à jour.
"""
import colorsys, json, os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")

# règle : (teinte min, teinte max) en degrés → (nouvelle teinte, × saturation, × luminosité)
BODY = (320, 25)
CREST = (35, 65)
VARIANTS = {
    "komainu": {  # base : sert de modèle aux variantes
        "komainu_flamerax": [(BODY, (14, 1.35, 1.0))],                               # Corona : rouge-orangé flamboyant
        "komainu_mizusar": [(BODY, (212, 1.1, 1.0)), (CREST, (190, 0.6, 1.0))],      # Flash : bleu, crête turquoise pâle
        "komainu_bombzar": [(BODY, (112, 0.95, 0.95)), (CREST, (60, 1.0, 1.0))],     # Aurora : vert, crête jaune-vert
        "komainu_optoger": [(BODY, (46, 1.1, 1.08)), (CREST, (50, 0.25, 1.05))],     # Halo : doré, crête presque blanche
        "komainu_scarazook": [(BODY, (28, 0.55, 0.72)), (CREST, (40, 0.5, 0.85))],   # Eclipse : brun terre, crête sable
    },
    "komanoto": {  # mêmes teintes que les Komainu de chaque lignée
        "komanoto_flamerax": [(BODY, (14, 1.35, 1.0))],
        "komanoto_mizusar": [(BODY, (212, 1.1, 1.0))],
        "komanoto_bombzar": [(BODY, (112, 0.95, 0.95))],
        "komanoto_optoger": [(BODY, (46, 1.1, 1.08))],
        "komanoto_scarazook": [(BODY, (28, 0.55, 0.72))],
    },
}


def in_range(h, lo, hi):
    return lo <= h <= hi if lo <= hi else (h >= lo or h <= hi)


def recolor(img, rules):
    out = img.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.12:  # noirs, blancs, gris : intacts
                continue
            for (lo, hi), (nh, ms, mv) in rules:
                if in_range(h * 360, lo, hi):
                    nr, ng, nb = colorsys.hsv_to_rgb(nh / 360, min(1, s * ms), min(1, v * mv))
                    px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
                    break
    return out


def main():
    for base, variants in VARIANTS.items():
        tex = Image.open(os.path.join(ASSETS, "textures/spectrobes", base + ".png")).convert("RGBA")
        res = json.load(open(os.path.join(ASSETS, "bedrock/spectrobes/resolvers", base + ".json"), encoding="utf-8"))
        for vid, rules in variants.items():
            recolor(tex, rules).save(os.path.join(ASSETS, "textures/spectrobes", vid + ".png"))
            r = dict(res, species=f"dirtrobes:{vid}", texture=f"dirtrobes:textures/spectrobes/{vid}.png")
            with open(os.path.join(ASSETS, "bedrock/spectrobes/resolvers", vid + ".json"), "w", encoding="utf-8") as fh:
                json.dump(r, fh, indent=2)
            print(vid)


if __name__ == "__main__":
    main()
